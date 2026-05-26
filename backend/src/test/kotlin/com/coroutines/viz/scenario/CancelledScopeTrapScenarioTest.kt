package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import com.coroutines.viz.scenario.TimelineTestHelper.stateChangesForNode
import kotlin.test.*

class CancelledScopeTrapScenarioTest {

    private val scenario = CancelledScopeTrapScenario()

    // ── Beginner: explicit cancel, then ghost launch is silently dropped ──

    @Test
    fun `beginner - launch after scope cancel transitions ghost directly New to Cancelled`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: scope → {first, ghost}
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // The real launch follows normal lifecycle
        assertNodeReachesFinalState(timeline, "first", JobState.Completed)

        // Scope is cancelled
        assertNodeReachesFinalState(timeline, "scope", JobState.Cancelled)

        // The ghost launch goes directly from New to Cancelled (one transition, no Active)
        val ghostChanges = stateChangesForNode(timeline, "ghost")
        assertEquals(1, ghostChanges.size,
            "Ghost launch should have exactly one state change (New → Cancelled)")
        assertEquals(JobState.New, ghostChanges.first().fromState)
        assertEquals(JobState.Cancelled, ghostChanges.first().toState,
            "Ghost launch must transition directly New → Cancelled (body never runs)")

        // The ghost's transition must happen AFTER the scope is cancelled
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val scopeCancelled = stateChanges.indexOfFirst { it.nodeId == "scope" && it.toState == JobState.Cancelled }
        val ghostCancelled = stateChanges.indexOfFirst { it.nodeId == "ghost" && it.toState == JobState.Cancelled }
        assertTrue(scopeCancelled < ghostCancelled,
            "Scope must be Cancelled BEFORE the ghost launch is attempted (that's the trap)")
    }

    // ── Intermediate: child failure cancels scope, subsequent launches dropped ──

    @Test
    fun `intermediate - child failure cancels scope, subsequent launches silently dropped`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: scope → {action-1, action-fails, action-3-ghost, action-4-ghost}
        assertEquals(5, collectNodeIds(timeline.tree).size)

        // First action completes normally
        assertNodeReachesFinalState(timeline, "action-1", JobState.Completed)

        // Failing action is cancelled
        assertNodeReachesFinalState(timeline, "action-fails", JobState.Cancelled)

        // Exception propagates from failing action to scope (it's a Job, not SupervisorJob)
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "action-fails" && it.targetNodeId == "scope" },
            "Exception must propagate from the failing action to the scope (Job, not SupervisorJob)")

        // Scope is killed
        assertNodeReachesFinalState(timeline, "scope", JobState.Cancelled)

        // Both ghost launches go directly New → Cancelled
        for (id in listOf("action-3-ghost", "action-4-ghost")) {
            val changes = stateChangesForNode(timeline, id)
            assertEquals(1, changes.size, "$id should have exactly one state change (silent drop)")
            assertEquals(JobState.New, changes.first().fromState)
            assertEquals(JobState.Cancelled, changes.first().toState,
                "$id must be silently dropped (New → Cancelled, body never runs)")
        }
    }

    // ── Advanced: side-by-side Job vs SupervisorJob ─────────────────────

    @Test
    fun `advanced - Job side drops launch #3, SupervisorJob side runs it`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees with correct builder/jobType pairings.
        // Both are CoroutineScope (factory) — the only difference is the internal Job type.
        // (BuilderType.SupervisorScope is reserved for the `supervisorScope { }` builder.)
        assertNotNull(timeline.secondTree, "Advanced level needs a secondTree for the side-by-side")
        assertEquals(BuilderType.CoroutineScope, timeline.tree.builder)
        assertEquals(JobType.Job, timeline.tree.jobType)
        assertEquals(BuilderType.CoroutineScope, timeline.secondTree!!.builder)
        assertEquals(JobType.SupervisorJob, timeline.secondTree!!.jobType)

        // 4 nodes per side (scope + 3 launches)
        assertEquals(4, collectNodeIds(timeline.tree).size)
        assertEquals(4, collectNodeIds(timeline.secondTree!!).size)

        // LEFT: #1 completes, #2 fails, scope dies, #3 silently dropped
        assertNodeReachesFinalState(timeline, "j-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "j-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "j-scope", JobState.Cancelled)
        val j3Changes = stateChangesForNode(timeline, "j-3")
        assertEquals(1, j3Changes.size, "LEFT #3 should have exactly one state change (silent drop)")
        assertEquals(JobState.New to JobState.Cancelled, j3Changes.first().fromState to j3Changes.first().toState,
            "LEFT #3 must be silently dropped (New → Cancelled)")

        // RIGHT: #1 completes, #2 fails (but isolated), scope stays alive, #3 completes
        assertNodeReachesFinalState(timeline, "s-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "s-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "s-3", JobState.Completed)

        // Exception event from j-2 should target j-scope (LEFT)
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "j-2" && it.targetNodeId == "j-scope" },
            "LEFT exception must propagate to its scope")

        // RIGHT must NOT have an ExceptionEvent targeting the supervisor scope
        assertTrue(exceptions.none { it.targetNodeId == "s-scope" },
            "RIGHT (SupervisorJob) must NOT show exception propagation to the supervisor — it goes to the handler")
    }
}
