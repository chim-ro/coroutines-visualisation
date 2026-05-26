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

class SupervisorJobScenarioTest {

    private val scenario = SupervisorJobScenario()

    // ── Beginner: Happy Path (supervisor isolates failure) ───────────

    @Test
    fun `beginner - failing child-1 does not cancel sibling child-2 under supervisorScope`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Root is supervisorScope with SupervisorJob
        assertEquals(BuilderType.SupervisorScope, timeline.tree.builder)
        assertEquals(JobType.SupervisorJob, timeline.tree.jobType)

        // child-1 fails, child-2 survives, root completes
        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // No cancellation events to siblings (supervisor absorbs)
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertEquals(0, cancellations.size, "SupervisorJob should NOT send cancellation to siblings")

        // Exception from a launch child of supervisorScope goes to the
        // CoroutineExceptionHandler, NOT to the supervisor — so no
        // ExceptionEvent should target the supervisor.
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.none { it.targetNodeId == "root" },
            "Exception from launch child of supervisorScope must not be drawn as propagating to the supervisor")
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - supervisor absorbs exception, 2 siblings complete normally`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root (supervisorScope) → {child-1, child-2 (fails), child-3}
        assertEquals(4, collectNodeIds(timeline.tree).size)

        // Only the failing child is cancelled
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)
        // Siblings complete normally
        assertNodeReachesFinalState(timeline, "child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child-3", JobState.Completed)
        // Supervisor completes (not cancelled!)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }

    // ── Advanced: Complex Case (nested coroutineScope inside supervisor) ─

    @Test
    fun `advanced - nested coroutineScope inside supervisor sibling completes despite failure in another branch`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {child-1, child-2 (fails), child-3 → scope → {inner-1, inner-2}, child-4}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(8, nodeIds.size)

        // child-2 fails
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)

        // All other nodes complete including deeply nested ones
        assertNodeReachesFinalState(timeline, "child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child-3", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child-4", JobState.Completed)
        assertNodeReachesFinalState(timeline, "scope", JobState.Completed)
        assertNodeReachesFinalState(timeline, "inner-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "inner-2", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Verify nested coroutineScope has correct builder type
        val child3 = timeline.tree.children.find { it.id == "child-3" }!!
        val nestedScope = child3.children.find { it.id == "scope" }!!
        assertEquals(BuilderType.CoroutineScope, nestedScope.builder)
        assertEquals(JobType.Job, nestedScope.jobType)

        assertAllNodesHaveEvents(timeline)
    }
}
