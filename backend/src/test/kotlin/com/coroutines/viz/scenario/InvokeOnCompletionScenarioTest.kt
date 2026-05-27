package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class InvokeOnCompletionScenarioTest {

    private val scenario = InvokeOnCompletionScenario()

    // ── Beginner: normal completion (cause = null) ──────────────────────

    @Test
    fun `beginner - child completes normally and scope completes`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(2, collectNodeIds(timeline.tree).size)
        assertNodeReachesFinalState(timeline, "child", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Child must reach a terminal state before the parent completes
        // (the callback fires on the terminal transition).
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val childCompleted = stateChanges.indexOfFirst { it.nodeId == "child" && it.toState == JobState.Completed }
        val rootCompleting = stateChanges.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completing }
        assertTrue(childCompleted < rootCompleting, "Child must complete before the parent starts completing")
    }

    // ── Intermediate: three distinct completion causes ──────────────────

    @Test
    fun `intermediate - one child completes, one fails, one is cancelled`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(4, collectNodeIds(timeline.tree).size) // root + 3 children

        // The three distinct terminal outcomes the callback's `cause` parameter reflects:
        assertNodeReachesFinalState(timeline, "child-1", JobState.Completed)   // cause = null
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)   // cause = the exception
        assertNodeReachesFinalState(timeline, "child-3", JobState.Cancelled)   // cause = CancellationException

        // child-2's failure propagates UP to the (non-supervisor) scope, which then cancels child-3.
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "child-2" && it.targetNodeId == "root" },
            "child-2's exception must propagate up to the coroutineScope")
        val cancels = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancels.any { it.sourceNodeId == "root" && it.targetNodeId == "child-3" },
            "the scope must cancel child-3 after receiving child-2's failure")

        // Ordering: child-1 completes (success) before child-2 fails (so the 'null cause'
        // case is shown first), and child-3 is cancelled only after child-2's failure.
        val sc = timeline.events.filterIsInstance<StateChangeEvent>()
        val c1Completed = sc.indexOfFirst { it.nodeId == "child-1" && it.toState == JobState.Completed }
        val c2Cancelled = sc.indexOfFirst { it.nodeId == "child-2" && it.toState == JobState.Cancelled }
        val c3Cancelled = sc.indexOfFirst { it.nodeId == "child-3" && it.toState == JobState.Cancelled }
        assertTrue(c1Completed < c2Cancelled, "success case shown before failure case")
        assertTrue(c2Cancelled < c3Cancelled, "child-3 cancelled only after child-2 failed")

        // Scope itself ends Cancelled (non-supervisor scope re-throws the child exception).
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)
    }

    // ── Advanced: parent callback fires only after ALL children complete ──

    @Test
    fun `advanced - parent invokeOnCompletion fires after every child is done`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(3, collectNodeIds(timeline.tree).size) // root + 2 children

        assertNodeReachesFinalState(timeline, "child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child-2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // BOTH children must reach Completed before the parent enters Completing —
        // that's the whole lesson: the parent callback waits for all children.
        val sc = timeline.events.filterIsInstance<StateChangeEvent>()
        val c1 = sc.indexOfFirst { it.nodeId == "child-1" && it.toState == JobState.Completed }
        val c2 = sc.indexOfFirst { it.nodeId == "child-2" && it.toState == JobState.Completed }
        val rootCompleting = sc.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completing }
        assertTrue(c1 < rootCompleting, "child-1 must complete before parent begins completing")
        assertTrue(c2 < rootCompleting, "child-2 must complete before parent begins completing")
    }
}
