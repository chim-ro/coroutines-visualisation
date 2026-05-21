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

class HappyPathScenarioTest {

    private val scenario = HappyPathScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - all nodes complete successfully in a simple parent-child tree`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → child-1
        assertEquals(2, collectNodeIds(timeline.tree).size, "Beginner should have 2 nodes")
        assertEquals("runBlocking", timeline.tree.displayName)
        assertEquals(1, timeline.tree.children.size)

        // Both nodes reach Completed
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child-1", JobState.Completed)

        // No exceptions or cancellations in happy path
        assertEquals(0, countEvents<ExceptionEvent>(timeline))
        assertEquals(0, countEvents<CancellationEvent>(timeline))
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - parent waits for grandchild before completing`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {child-1 → grandchild-1, child-2, child-3}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(5, nodeIds.size, "Intermediate should have 5 nodes")
        assertTrue("grandchild-1" in nodeIds, "Should have grandchild-1")

        // Grandchild must complete BEFORE its parent (child-1)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val gcCompleted = stateChanges.indexOfFirst { it.nodeId == "grandchild-1" && it.toState == JobState.Completed }
        val child1Completed = stateChanges.indexOfFirst { it.nodeId == "child-1" && it.toState == JobState.Completed }
        assertTrue(gcCompleted < child1Completed,
            "Grandchild must complete before parent (structured concurrency)")

        // Root completes last
        val rootCompleted = stateChanges.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completed }
        assertTrue(rootCompleted > child1Completed, "Root must complete after all children")
    }

    // ── Advanced: Complex Case ───────────────────────────────────────

    @Test
    fun `advanced - 9-node tree completes bottom-up with correct structured concurrency ordering`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // 3 children × 2 grandchildren each + root = 10 total
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(10, nodeIds.size, "Advanced should have 10 nodes (root + 3 children + 6 grandchildren)")

        // All nodes reach Completed
        for (id in nodeIds) {
            assertNodeReachesFinalState(timeline, id, JobState.Completed)
        }

        // Verify bottom-up completion ordering: grandchildren before children before root
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun completedIndex(nodeId: String) =
            stateChanges.indexOfFirst { it.nodeId == nodeId && it.toState == JobState.Completed }

        // gc-1a and gc-1b must complete before child-1
        assertTrue(completedIndex("gc-1a") < completedIndex("child-1"))
        assertTrue(completedIndex("gc-1b") < completedIndex("child-1"))
        // gc-2a and gc-2b must complete before child-2
        assertTrue(completedIndex("gc-2a") < completedIndex("child-2"))
        assertTrue(completedIndex("gc-2b") < completedIndex("child-2"))
        // gc-3a and gc-3b must complete before child-3
        assertTrue(completedIndex("gc-3a") < completedIndex("child-3"))
        assertTrue(completedIndex("gc-3b") < completedIndex("child-3"))
        // All children must complete before root
        assertTrue(completedIndex("child-1") < completedIndex("root"))
        assertTrue(completedIndex("child-2") < completedIndex("root"))
        assertTrue(completedIndex("child-3") < completedIndex("root"))

        assertAllNodesHaveEvents(timeline)
    }
}
