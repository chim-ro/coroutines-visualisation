package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class AsyncPropagationContrastScenarioTest {

    private val scenario = AsyncPropagationContrastScenario()

    // ── Beginner: minimal contrast — coroutineScope cancels, supervisorScope swallows ──

    @Test
    fun `beginner - coroutineScope cancels on async failure, supervisorScope completes (exception silently lost)`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees with proper builder/jobType pairings
        assertNotNull(timeline.secondTree, "Side-by-side scenario needs a secondTree")
        assertEquals(BuilderType.CoroutineScope, timeline.tree.builder)
        assertEquals(JobType.Job, timeline.tree.jobType)
        assertEquals(BuilderType.SupervisorScope, timeline.secondTree!!.builder)
        assertEquals(JobType.SupervisorJob, timeline.secondTree!!.jobType)

        // LEFT: scope ends Cancelled
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-async", JobState.Cancelled)

        // RIGHT: scope ends Completed (exception silently lost)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-async", JobState.Cancelled)

        // LEFT must have an ExceptionEvent from async to scope (immediate propagation)
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "cs-async" && it.targetNodeId == "cs-root" },
            "LEFT (coroutineScope) must show immediate exception propagation from async to scope")

        // RIGHT must NOT have an ExceptionEvent targeting the supervisor (exception held in Deferred)
        assertTrue(exceptions.none { it.targetNodeId == "ss-root" },
            "RIGHT (supervisorScope) must NOT show exception propagation — the exception is held in the Deferred")
    }

    // ── Intermediate: sibling is cancelled on the left, completes on the right ──

    @Test
    fun `intermediate - sibling cancelled under coroutineScope but completes under supervisorScope`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Both trees have parent + async + sibling = 3 nodes
        assertEquals(3, collectNodeIds(timeline.tree).size)
        assertNotNull(timeline.secondTree)
        assertEquals(3, collectNodeIds(timeline.secondTree!!).size)

        // LEFT: async fails, sibling is cancelled, scope cancelled
        assertNodeReachesFinalState(timeline, "cs-async", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-sibling", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)

        // RIGHT: async fails (Cancelled), sibling completes, scope completes (exception lost)
        assertNodeReachesFinalState(timeline, "ss-async", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "ss-sibling", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Completed)

        // LEFT must have a CancellationEvent from scope to sibling
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "cs-root" && it.targetNodeId == "cs-sibling" },
            "LEFT (coroutineScope) must cancel its sibling after async failure")

        // RIGHT must NOT have any CancellationEvents (supervisor doesn't cascade)
        assertTrue(cancellations.none { it.sourceNodeId == "ss-root" },
            "RIGHT (supervisorScope) must not cancel siblings on async failure")
    }

    // ── Advanced: with .await() — supervisor's scope eventually cancelled too ──

    @Test
    fun `advanced - coroutineScope cancels siblings before await, supervisor cancels itself after await rethrows`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // 4 nodes per side: parent + async + 2 siblings
        assertEquals(4, collectNodeIds(timeline.tree).size)
        assertNotNull(timeline.secondTree)
        assertEquals(4, collectNodeIds(timeline.secondTree!!).size)

        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun completedIdx(nodeId: String) = stateChanges.indexOfFirst {
            it.nodeId == nodeId && it.toState == JobState.Completed
        }
        fun cancelledIdx(nodeId: String) = stateChanges.indexOfFirst {
            it.nodeId == nodeId && it.toState == JobState.Cancelled
        }

        // LEFT: both siblings end Cancelled
        assertNodeReachesFinalState(timeline, "cs-sibling-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-sibling-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)

        // RIGHT: both siblings COMPLETE (work preserved), then scope ends Cancelled (await rethrows)
        assertNodeReachesFinalState(timeline, "ss-sibling-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-sibling-2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Cancelled)

        // RIGHT siblings must complete BEFORE the supervisor is cancelled (await is at the end)
        val ssRootCancelled = cancelledIdx("ss-root")
        assertTrue(completedIdx("ss-sibling-1") < ssRootCancelled,
            "Right sibling #1 must complete before the supervisor is cancelled (await happens later)")
        assertTrue(completedIdx("ss-sibling-2") < ssRootCancelled,
            "Right sibling #2 must complete before the supervisor is cancelled (await happens later)")

        // RIGHT must have an ExceptionEvent at the end (await rethrows)
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.targetNodeId == "ss-root" },
            "RIGHT must show an exception propagating to the supervisor when .await() is finally called")
    }
}
