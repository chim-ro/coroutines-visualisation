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

class ChildExceptionScenarioTest {

    private val scenario = ChildExceptionScenario()

    // ── Beginner: Happy Path (single child exception) ────────────────

    @Test
    fun `beginner - single child exception propagates to parent and cancels scope`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root (coroutineScope) → child (fails)
        assertEquals(2, collectNodeIds(timeline.tree).size)
        assertEquals(BuilderType.CoroutineScope, timeline.tree.builder)

        // Child fails, parent scope is cancelled
        assertNodeReachesFinalState(timeline, "child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)

        // Exception event from child to root
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertEquals(1, exceptions.size, "Should have exactly 1 exception event")
        assertEquals("child", exceptions[0].sourceNodeId)
        assertEquals("root", exceptions[0].targetNodeId)
        assertTrue(exceptions[0].exceptionMessage.contains("RuntimeException"))
    }

    // ── Intermediate: Failure Case (exception cancels siblings) ──────

    @Test
    fun `intermediate - exception in child-2 cancels siblings child-1 and child-3`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {child-1, child-2 (fails), child-3}
        assertEquals(4, collectNodeIds(timeline.tree).size)

        // child-2 fails, siblings are cancelled by parent
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-3", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Cancelled)

        // CancellationEvents from root to siblings
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "root" && it.targetNodeId == "child-1" })
        assertTrue(cancellations.any { it.sourceNodeId == "root" && it.targetNodeId == "child-3" })

        // Exception should propagate from child-2 to root
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "child-2" && it.targetNodeId == "root" })
    }

    // ── Advanced: Complex Case (deep exception propagation) ──────────

    @Test
    fun `advanced - exception propagates from grandchild through child-2 to root, cancelling all siblings`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {child-1, child-2 → grandchild (fails), child-3, child-4}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(6, nodeIds.size)

        // Exception chain: grandchild → child-2 → root
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "grandchild" && it.targetNodeId == "child-2" },
            "Exception must propagate from grandchild to child-2")
        assertTrue(exceptions.any { it.sourceNodeId == "child-2" && it.targetNodeId == "root" },
            "Exception must propagate from child-2 to root")

        // All nodes end up cancelled
        for (id in nodeIds) {
            assertNodeReachesFinalState(timeline, id, JobState.Cancelled)
        }

        // Verify cancellation ordering: grandchild fails first, then child-2, then siblings
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun cancelledIdx(id: String) = stateChanges.indexOfFirst { it.nodeId == id && it.toState == JobState.Cancelled }
        assertTrue(cancelledIdx("grandchild") < cancelledIdx("child-2"))
        assertTrue(cancelledIdx("child-2") < cancelledIdx("child-1"))
        assertTrue(cancelledIdx("child-2") < cancelledIdx("child-3"))
        assertTrue(cancelledIdx("child-2") < cancelledIdx("child-4"))

        assertAllNodesHaveEvents(timeline)
    }
}
