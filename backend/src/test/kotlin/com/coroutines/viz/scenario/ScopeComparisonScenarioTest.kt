package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class ScopeComparisonScenarioTest {

    private val scenario = ScopeComparisonScenario()

    // ── Beginner: Happy Path (side-by-side comparison) ───────────────

    @Test
    fun `beginner - coroutineScope cancels sibling while supervisorScope preserves it`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Must have two trees
        assertNotNull(timeline.secondTree, "Scope comparison must have a secondTree")

        // Left tree: coroutineScope, Right tree: supervisorScope
        assertEquals(BuilderType.CoroutineScope, timeline.tree.builder)
        assertEquals(BuilderType.SupervisorScope, timeline.secondTree!!.builder)

        // coroutineScope side: all cancelled
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-child-2", JobState.Cancelled)

        // supervisorScope side: only failing child cancelled, sibling and root complete
        assertNodeReachesFinalState(timeline, "ss-child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "ss-child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Completed)
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - coroutineScope cancels both siblings, supervisorScope preserves both`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Both trees have 3 children each
        assertEquals(4, collectNodeIds(timeline.tree).size) // cs-root + 3 children
        assertEquals(4, collectNodeIds(timeline.secondTree!!).size)

        // CS: all cancelled
        assertNodeReachesFinalState(timeline, "cs-child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-child-3", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)

        // SS: only child-2 cancelled, rest complete
        assertNodeReachesFinalState(timeline, "ss-child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "ss-child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-child-3", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Completed)
    }

    // ── Advanced: Complex Case (grandchildren in comparison) ─────────

    @Test
    fun `advanced - coroutineScope cancels entire tree including grandchildren, supervisorScope preserves them`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // CS tree has grandchildren
        val csNodeIds = collectNodeIds(timeline.tree)
        assertTrue("cs-grandchild-1a" in csNodeIds, "CS tree should have grandchildren")
        assertTrue("cs-grandchild-1b" in csNodeIds)

        // CS: everything cancelled including grandchildren
        assertNodeReachesFinalState(timeline, "cs-grandchild-1a", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-grandchild-1b", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "cs-root", JobState.Cancelled)

        // SS: grandchildren complete normally
        assertNodeReachesFinalState(timeline, "ss-grandchild-1a", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-grandchild-1b", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-child-1", JobState.Completed)
        assertNodeReachesFinalState(timeline, "ss-root", JobState.Completed)

        // coroutineScope side: exception propagates to the scope.
        // supervisorScope side: exception goes to the CoroutineExceptionHandler, NOT to the supervisor.
        val exceptions = timeline.events.filterIsInstance<ExceptionEvent>()
        assertTrue(exceptions.any { it.sourceNodeId == "cs-child-2" && it.targetNodeId == "cs-root" })
        assertTrue(exceptions.none { it.targetNodeId == "ss-root" },
            "Exception from a launch child of supervisorScope must not propagate to the supervisor")
    }
}
