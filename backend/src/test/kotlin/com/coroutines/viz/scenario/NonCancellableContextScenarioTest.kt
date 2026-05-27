package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class NonCancellableContextScenarioTest {

    private val scenario = NonCancellableContextScenario()

    // ── Beginner: cleanup runs during cancellation, child ends Cancelled ──

    @Test
    fun `beginner - child runs cleanup while Cancelling, then ends Cancelled`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(2, collectNodeIds(timeline.tree).size)

        // Both child and scope end Cancelled (scope was explicitly cancelled).
        assertNodeReachesFinalState(timeline, "child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)

        // The child must sit in Cancelling for a while (cleanup) before reaching Cancelled —
        // i.e. there are narrative events between Cancelling and Cancelled.
        val events = timeline.events
        val cancellingIdx = events.indexOfFirst {
            it is StateChangeEvent && it.nodeId == "child" && it.toState == JobState.Cancelling
        }
        val cancelledIdx = events.indexOfFirst {
            it is StateChangeEvent && it.nodeId == "child" && it.toState == JobState.Cancelled
        }
        assertTrue(cancellingIdx in 0 until cancelledIdx,
            "Child must enter Cancelling before Cancelled")
        val narrativesDuringCleanup = events.subList(cancellingIdx, cancelledIdx).count { it is NarrativeEvent }
        assertTrue(narrativesDuringCleanup >= 1,
            "There should be cleanup narrative while the child is in Cancelling (NonCancellable work runs)")

        // The parent must reach Cancelled only AFTER the child does (it waits for cleanup).
        val parentCancelled = events.indexOfFirst {
            it is StateChangeEvent && it.nodeId == "root" && it.toState == JobState.Cancelled
        }
        assertTrue(cancelledIdx < parentCancelled, "Parent ends Cancelled only after the child's cleanup finishes")
    }

    // ── Intermediate: NonCancellable child takes longer to finish than the normal one ──

    @Test
    fun `intermediate - normal child cancels instantly, NonCancellable child finishes cleanup later`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(3, collectNodeIds(timeline.tree).size) // root + 2 children

        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)

        // The normal child (#1) reaches Cancelled BEFORE the NonCancellable child (#2),
        // because #2 has to finish its cleanup block first.
        val sc = timeline.events.filterIsInstance<StateChangeEvent>()
        val c1Cancelled = sc.indexOfFirst { it.nodeId == "child-1" && it.toState == JobState.Cancelled }
        val c2Cancelled = sc.indexOfFirst { it.nodeId == "child-2" && it.toState == JobState.Cancelled }
        assertTrue(c1Cancelled < c2Cancelled,
            "Normal child cancels instantly; NonCancellable child finishes cleanup later")
    }

    // ── Advanced: a NEW coroutine launched during cleanup runs to completion ──

    @Test
    fun `advanced - coroutine launched inside NonCancellable runs and completes during cancellation`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → child → cleanup-child
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // The cleanup coroutine actually runs and COMPLETES (not cancelled) even though
        // its lexical parent is being cancelled — that's the NonCancellable guarantee.
        assertNodeReachesFinalState(timeline, "cleanup-child", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)

        val sc = timeline.events.filterIsInstance<StateChangeEvent>()
        // The cleanup child only STARTS after the outer child has entered Cancelling.
        val childCancelling = sc.indexOfFirst { it.nodeId == "child" && it.toState == JobState.Cancelling }
        val cleanupActive = sc.indexOfFirst { it.nodeId == "cleanup-child" && it.toState == JobState.Active }
        assertTrue(childCancelling in 0 until cleanupActive,
            "Cleanup coroutine is launched only after the outer child is already Cancelling")

        // The cleanup child COMPLETES before the outer child is finally Cancelled.
        val cleanupCompleted = sc.indexOfFirst { it.nodeId == "cleanup-child" && it.toState == JobState.Completed }
        val childCancelled = sc.indexOfFirst { it.nodeId == "child" && it.toState == JobState.Cancelled }
        assertTrue(cleanupCompleted < childCancelled,
            "Cleanup coroutine completes before the outer child reaches its terminal Cancelled state")
    }
}
