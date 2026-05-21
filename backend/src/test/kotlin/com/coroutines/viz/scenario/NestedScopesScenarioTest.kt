package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.assertAllNodesHaveEvents
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class NestedScopesScenarioTest {

    private val scenario = NestedScopesScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - simple 2-level nesting with supervisor containing failure`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → supervisor → {child-a, child-b (fails)}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(4, nodeIds.size)

        // Verify supervisor scope hierarchy
        val supervisor = timeline.tree.children[0]
        assertEquals("supervisorScope", supervisor.displayName)
        assertEquals(JobType.SupervisorJob, supervisor.jobType)

        // child-b fails, child-a survives, supervisor and root complete
        assertNodeReachesFinalState(timeline, "child-b", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-a", JobState.Completed)
        assertNodeReachesFinalState(timeline, "supervisor", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - 4-level tree with supervisor isolating branch B failure from A and C`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → supervisor → {branch-a → {a1, a2}, branch-b (fails) → b1, branch-c}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(8, nodeIds.size)

        // B subtree fails
        assertNodeReachesFinalState(timeline, "b1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "branch-b", JobState.Cancelled)

        // A subtree and C survive
        assertNodeReachesFinalState(timeline, "a1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "a2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "branch-a", JobState.Completed)
        assertNodeReachesFinalState(timeline, "branch-c", JobState.Completed)

        // Exception chain: b1 → branch-b → supervisor
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "b1" && it.targetNodeId == "branch-b" })
        assertTrue(exceptions.any { it.sourceNodeId == "branch-b" && it.targetNodeId == "supervisor" })

        assertAllNodesHaveEvents(timeline)
    }

    // ── Advanced: Complex Case ───────────────────────────────────────

    @Test
    fun `advanced - 5-level tree with coroutineScope nested inside supervisor branch`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → supervisor → {branch-a → scope-a → {a1, a2}, branch-b → b1, branch-c → c1}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(10, nodeIds.size)

        // Nested coroutineScope inside branch-a
        val branchA = timeline.tree.children[0].children[0] // supervisor → branch-a
        val scopeA = branchA.children[0]
        assertEquals("coroutineScope", scopeA.displayName)
        assertEquals(BuilderType.CoroutineScope, scopeA.builder)

        // B subtree fails, everything else completes
        assertNodeReachesFinalState(timeline, "b1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "branch-b", JobState.Cancelled)

        // A subtree with nested coroutineScope completes
        assertNodeReachesFinalState(timeline, "a1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "a2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "scope-a", JobState.Completed)
        assertNodeReachesFinalState(timeline, "branch-a", JobState.Completed)

        // C subtree completes
        assertNodeReachesFinalState(timeline, "c1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "branch-c", JobState.Completed)

        // Root hierarchy completes
        assertNodeReachesFinalState(timeline, "supervisor", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }
}
