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

class CoroutineContextScenarioTest {

    private val scenario = CoroutineContextScenario()

    // ── Beginner: Happy Path (context inheritance) ───────────────────

    @Test
    fun `beginner - child inherits parent context and both complete normally`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → child-inherits
        assertEquals(2, collectNodeIds(timeline.tree).size)

        assertNodeReachesFinalState(timeline, "child-inherits", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Child must complete before root
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val childCompleted = stateChanges.indexOfFirst { it.nodeId == "child-inherits" && it.toState == JobState.Completed }
        val rootCompleting = stateChanges.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completing }
        assertTrue(childCompleted < rootCompleting, "Child should complete before root starts completing")
    }

    // ── Intermediate: Failure/Edge Case (Job() breaks structured concurrency) ──

    @Test
    fun `intermediate - orphaned launch with Job() completes after parent, breaking structured concurrency`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Main tree: root → {child-inherits, child-named, child-broken}
        // Orphan lives in secondTree to reflect that it's NOT structurally a child.
        val mainIds = collectNodeIds(timeline.tree)
        assertEquals(4, mainIds.size)
        assertNotNull(timeline.secondTree, "Orphan must be in secondTree (detached)")
        assertEquals("orphan", timeline.secondTree!!.id)

        // BUG DETECTION: The orphan (launch(Job())) completes AFTER root completes.
        // This is the intentional demonstration of broken structured concurrency.
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val rootCompleted = stateChanges.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completed }
        val orphanCompleted = stateChanges.indexOfFirst { it.nodeId == "orphan" && it.toState == JobState.Completed }

        assertTrue(rootCompleted < orphanCompleted,
            "Orphan should complete AFTER root (broken structured concurrency)")

        // child-broken completes without waiting for orphan
        val childBrokenCompleted = stateChanges.indexOfFirst { it.nodeId == "child-broken" && it.toState == JobState.Completed }
        assertTrue(childBrokenCompleted < orphanCompleted,
            "child-broken should complete before orphan (doesn't wait for launch(Job()))")

        // All main-tree nodes eventually complete, plus the detached orphan
        for (id in mainIds) {
            assertNodeReachesFinalState(timeline, id, JobState.Completed)
        }
        assertNodeReachesFinalState(timeline, "orphan", JobState.Completed)
    }

    // ── Advanced: Complex Case (multiple context overrides + Job() danger) ──

    @Test
    fun `advanced - 4 children with context overrides and orphaned Job() demonstrate full context mechanics`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Main tree: root → {child-inherits, child-named, child-dispatched, child-broken}
        // Orphan lives in secondTree to reflect that it's NOT structurally a child.
        val mainIds = collectNodeIds(timeline.tree)
        assertEquals(5, mainIds.size)
        assertNotNull(timeline.secondTree, "Orphan must be in secondTree (detached)")
        assertEquals("orphan", timeline.secondTree!!.id)

        // All normal children complete before root
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        fun completedIdx(id: String) = stateChanges.indexOfFirst { it.nodeId == id && it.toState == JobState.Completed }

        assertTrue(completedIdx("child-inherits") < completedIdx("root"))
        assertTrue(completedIdx("child-named") < completedIdx("root"))
        assertTrue(completedIdx("child-dispatched") < completedIdx("root"))
        assertTrue(completedIdx("child-broken") < completedIdx("root"))

        // Orphan completes after root (broken structured concurrency)
        assertTrue(completedIdx("orphan") > completedIdx("root"),
            "Orphan should complete after root — proving Job() breaks the parent-child link")

        // Verify all tree children have correct builder types
        assertEquals(4, timeline.tree.children.size)
        assertTrue(timeline.tree.children.all { it.builder == BuilderType.Launch })
    }
}
