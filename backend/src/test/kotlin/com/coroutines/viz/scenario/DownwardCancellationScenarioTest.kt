package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.assertAllNodesHaveEvents
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import com.coroutines.viz.scenario.TimelineTestHelper.countEvents
import kotlin.test.*

class DownwardCancellationScenarioTest {

    private val scenario = DownwardCancellationScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - parent cancellation propagates to single child`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → parent → child
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // Parent and child should be Cancelled, root should Complete
        assertNodeReachesFinalState(timeline, "parent", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // There should be a CancellationEvent from parent to child
        val cancelEvents = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancelEvents.any { it.sourceNodeId == "parent" && it.targetNodeId == "child" },
            "Should have cancellation event from parent to child")

        // Child must be cancelled before parent
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val childCancelled = stateChanges.indexOfFirst { it.nodeId == "child" && it.toState == JobState.Cancelled }
        val parentCancelled = stateChanges.indexOfFirst { it.nodeId == "parent" && it.toState == JobState.Cancelled }
        assertTrue(childCancelled < parentCancelled, "Child must be cancelled before parent (bottom-up)")
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - cancellation cascades through 3 levels with staggered timing`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → parent → {child-1 → grandchild-1, child-2}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(5, nodeIds.size)

        // All descendants cancelled, root completes
        assertNodeReachesFinalState(timeline, "grandchild-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "parent", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Cancellation flows: parent → child-1, parent → child-2, child-1 → grandchild-1
        val cancelEvents = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancelEvents.any { it.sourceNodeId == "parent" && it.targetNodeId == "child-1" })
        assertTrue(cancelEvents.any { it.sourceNodeId == "parent" && it.targetNodeId == "child-2" })
        assertTrue(cancelEvents.any { it.sourceNodeId == "child-1" && it.targetNodeId == "grandchild-1" })

        // Bottom-up cancellation order: grandchild → siblings → children → parent
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun cancelledIdx(id: String) = stateChanges.indexOfFirst { it.nodeId == id && it.toState == JobState.Cancelled }
        assertTrue(cancelledIdx("grandchild-1") < cancelledIdx("child-1"))
        assertTrue(cancelledIdx("child-1") < cancelledIdx("parent"))
    }

    // ── Advanced: Complex Case ───────────────────────────────────────

    @Test
    fun `advanced - already-completed child is unaffected by parent cancellation`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → parent → {child-1 → {gc-1, gc-2}, child-2, child-3}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(7, nodeIds.size)

        // child-2 completes BEFORE cancellation starts
        assertNodeReachesFinalState(timeline, "child-2", JobState.Completed)

        // All other descendants are cancelled
        assertNodeReachesFinalState(timeline, "gc-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "gc-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-3", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "parent", JobState.Cancelled)

        // child-2 completes before parent enters Cancelling
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val child2Completed = stateChanges.indexOfFirst { it.nodeId == "child-2" && it.toState == JobState.Completed }
        val parentCancelling = stateChanges.indexOfFirst { it.nodeId == "parent" && it.toState == JobState.Cancelling }
        assertTrue(child2Completed < parentCancelling,
            "child-2 must complete before parent begins cancelling")

        assertAllNodesHaveEvents(timeline)
    }
}
