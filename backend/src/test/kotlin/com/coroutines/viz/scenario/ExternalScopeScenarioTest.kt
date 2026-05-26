package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class ExternalScopeScenarioTest {

    private val scenario = ExternalScopeScenario()

    // ── Beginner: handler completes while external child still runs ──────

    @Test
    fun `beginner - handler completes before externalScope's background child finishes`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees: handler (LEFT) + externalScope (RIGHT)
        assertNotNull(timeline.secondTree, "External-scope scenario needs a secondTree")
        assertEquals("h-scope", timeline.tree.id)
        assertEquals("x-scope", timeline.secondTree!!.id)

        // External scope's JobType is SupervisorJob (it's CoroutineScope(SupervisorJob()))
        assertEquals(JobType.SupervisorJob, timeline.secondTree!!.jobType,
            "externalScope must use SupervisorJob so a child failure doesn't kill the scope")

        // Handler completes; bg work completes; external scope STAYS Active
        // (a real app-level scope is long-lived — it doesn't auto-complete just
        //  because one background task finished)
        assertNodeReachesFinalState(timeline, "h-main", JobState.Completed)
        assertNodeReachesFinalState(timeline, "h-scope", JobState.Completed)
        assertNodeReachesFinalState(timeline, "x-bg", JobState.Completed)
        assertNodeReachesFinalState(timeline, "x-scope", JobState.Active)

        // The handler MUST complete BEFORE the background work completes
        // (handler returns immediately, doesn't wait for bg)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun completedIdx(id: String) = stateChanges.indexOfFirst {
            it.nodeId == id && it.toState == JobState.Completed
        }
        assertTrue(completedIdx("h-scope") < completedIdx("x-bg"),
            "handler-scope must complete BEFORE background work — that's the whole point of fire-and-forget")
    }

    // ── Intermediate: app shutdown cleanly cancels long-running bg work ──

    @Test
    fun `intermediate - externalScope cancel() cleanly cancels long-running background work`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Handler completes normally
        assertNodeReachesFinalState(timeline, "h-main", JobState.Completed)
        assertNodeReachesFinalState(timeline, "h-scope", JobState.Completed)

        // Background work is cancelled (not completed) — app shut down before it finished
        assertNodeReachesFinalState(timeline, "x-bg", JobState.Cancelled)

        // External scope ends Cancelled (we cancelled it explicitly)
        assertNodeReachesFinalState(timeline, "x-scope", JobState.Cancelled)

        // There must be a CancellationEvent from externalScope to its background child
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "x-scope" && it.targetNodeId == "x-bg" },
            "externalScope.cancel() must propagate to its bg child")

        // Handler must complete BEFORE the cancellation event (cancel happens at shutdown, after handler returned)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val handlerCompleted = stateChanges.indexOfFirst { it.nodeId == "h-scope" && it.toState == JobState.Completed }
        val bgCancelling = stateChanges.indexOfFirst { it.nodeId == "x-bg" && it.toState == JobState.Cancelling }
        assertTrue(handlerCompleted < bgCancelling,
            "Handler must complete before bg work is cancelled — shutdown happens after handler returned")
    }

    // ── Advanced: side-by-side — orphan can't be cancelled, externalScope child can ──

    @Test
    fun `advanced - orphan keeps running through shutdown, externalScope child is cancelled cleanly`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // LEFT tree is just the orphan (single node)
        assertEquals("orphan", timeline.tree.id)
        assertEquals(1, collectNodeIds(timeline.tree).size,
            "LEFT (orphan world) is a single detached node")

        // RIGHT tree has externalScope + bg child
        assertNotNull(timeline.secondTree)
        assertEquals("x-scope", timeline.secondTree!!.id)
        assertEquals(2, collectNodeIds(timeline.secondTree!!).size)
        assertEquals(JobType.SupervisorJob, timeline.secondTree!!.jobType)

        // LEFT: orphan ends Completed (eventually finishes on its own — couldn't be cancelled)
        assertNodeReachesFinalState(timeline, "orphan", JobState.Completed)

        // RIGHT: bg work is Cancelled (cleanly), externalScope is Cancelled
        assertNodeReachesFinalState(timeline, "x-bg", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "x-scope", JobState.Cancelled)

        // RIGHT has a CancellationEvent from externalScope to its bg child; LEFT has none
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "x-scope" && it.targetNodeId == "x-bg" },
            "RIGHT: externalScope cancels its bg child")
        assertTrue(cancellations.none { it.targetNodeId == "orphan" },
            "LEFT: orphan must not receive any CancellationEvent — there's no scope that can reach it")

        // RIGHT bg-work must reach Cancelled BEFORE LEFT orphan reaches Completed
        // (orphan is "still running" while RIGHT shut down cleanly)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val bgCancelled = stateChanges.indexOfFirst { it.nodeId == "x-bg" && it.toState == JobState.Cancelled }
        val orphanCompleted = stateChanges.indexOfFirst { it.nodeId == "orphan" && it.toState == JobState.Completed }
        assertTrue(bgCancelled < orphanCompleted,
            "RIGHT bg work must be cleaned up BEFORE LEFT orphan naturally completes — proves the orphan leaked past shutdown")
    }
}
