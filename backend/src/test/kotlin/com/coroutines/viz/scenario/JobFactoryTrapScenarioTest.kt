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

class JobFactoryTrapScenarioTest {

    private val scenario = JobFactoryTrapScenario()

    // ── Beginner: Job() stays Active after child completes, complete() unsticks it ──

    @Test
    fun `beginner - manual Job stays Active after child completes, then complete() finishes it`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: manual-job → child
        assertEquals(2, collectNodeIds(timeline.tree).size)

        // The CHILD must reach Completed BEFORE the manual Job's first transition out of Active
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val childCompleted = stateChanges.indexOfFirst { it.nodeId == "child" && it.toState == JobState.Completed }
        val manualJobLeavesActive = stateChanges.indexOfFirst { it.nodeId == "manual-job" && it.fromState == JobState.Active }

        assertTrue(childCompleted < manualJobLeavesActive,
            "The child must finish BEFORE the manual Job leaves Active — that's the surprise being demonstrated")

        // The manual Job must reach Completed eventually (after complete() is conceptually called)
        assertNodeReachesFinalState(timeline, "manual-job", JobState.Completed)
        assertNodeReachesFinalState(timeline, "child", JobState.Completed)
    }

    // ── Intermediate: join() would deadlock, complete() rescues ──────────

    @Test
    fun `intermediate - manual Job stays Active after all children complete, hangs join() until complete()`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: manual-job → {child-1, child-2}
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // Both children reach Completed BEFORE the manual Job leaves Active
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val child1Completed = stateChanges.indexOfFirst { it.nodeId == "child-1" && it.toState == JobState.Completed }
        val child2Completed = stateChanges.indexOfFirst { it.nodeId == "child-2" && it.toState == JobState.Completed }
        val manualJobCompleting = stateChanges.indexOfFirst { it.nodeId == "manual-job" && it.toState == JobState.Completing }

        assertTrue(child1Completed < manualJobCompleting,
            "Child #1 must complete BEFORE the manual Job — the trap is that Job() doesn't auto-complete")
        assertTrue(child2Completed < manualJobCompleting,
            "Child #2 must complete BEFORE the manual Job — the trap is that Job() doesn't auto-complete")

        // The manual Job's lifecycle: New → Active → Completing → Completed (only after complete() conceptually called)
        val manualJobChanges = stateChangesForNode(timeline, "manual-job")
        val manualJobStates = listOf(manualJobChanges.first().fromState) + manualJobChanges.map { it.toState }
        assertEquals(
            listOf(JobState.New, JobState.Active, JobState.Completing, JobState.Completed),
            manualJobStates,
            "Manual Job follows the standard completion lifecycle — but only after complete() is called"
        )
    }

    // ── Advanced: side-by-side — manual Job needs complete(), coroutineScope doesn't ──

    @Test
    fun `advanced - manual Job vs coroutineScope side-by-side, only coroutineScope auto-completes`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees: manual-job + auto-scope
        assertNotNull(timeline.secondTree, "Advanced level must have a secondTree for side-by-side comparison")
        assertEquals("manual-job", timeline.tree.id)
        assertEquals("auto-scope", timeline.secondTree!!.id)

        // Both sides have 2 children + 1 parent = 3 nodes
        assertEquals(3, collectNodeIds(timeline.tree).size)
        assertEquals(3, collectNodeIds(timeline.secondTree!!).size)

        // All four children reach Completed
        for (id in listOf("m-child-1", "m-child-2", "a-child-1", "a-child-2")) {
            assertNodeReachesFinalState(timeline, id, JobState.Completed)
        }

        // Both parents reach Completed
        assertNodeReachesFinalState(timeline, "manual-job", JobState.Completed)
        assertNodeReachesFinalState(timeline, "auto-scope", JobState.Completed)

        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun completedIdx(nodeId: String) = stateChanges.indexOfFirst {
            it.nodeId == nodeId && it.toState == JobState.Completed
        }

        // RIGHT side: coroutineScope completes BEFORE the manual Job (because it auto-completes
        // as soon as its children are done, whereas manual-job needs explicit complete())
        val autoCompleted = completedIdx("auto-scope")
        val manualCompleted = completedIdx("manual-job")
        assertTrue(autoCompleted < manualCompleted,
            "coroutineScope must complete BEFORE the manual Job (auto-completion vs requires-complete())")

        // Both right-side children complete before the auto-scope (proving it waited for them)
        assertTrue(completedIdx("a-child-1") < autoCompleted,
            "Right child #1 must complete before its parent coroutineScope")
        assertTrue(completedIdx("a-child-2") < autoCompleted,
            "Right child #2 must complete before its parent coroutineScope")
    }
}
