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

class LazyStartScenarioTest {

    private val scenario = LazyStartScenario()

    // ── Beginner: one eager + one lazy started via .start() ───────────

    @Test
    fun `beginner - eager child auto-starts, lazy child stays New until start() is called`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {eager, lazy}
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // Eager goes New → Active → Completing → Completed
        val eagerChanges = stateChangesForNode(timeline, "eager")
        val eagerStates = listOf(eagerChanges.first().fromState) + eagerChanges.map { it.toState }
        assertEquals(
            listOf(JobState.New, JobState.Active, JobState.Completing, JobState.Completed),
            eagerStates,
            "Eager child should follow standard lifecycle"
        )

        // Lazy goes New → Active → Completing → Completed (same end states, but Active comes later)
        val lazyChanges = stateChangesForNode(timeline, "lazy")
        val lazyStates = listOf(lazyChanges.first().fromState) + lazyChanges.map { it.toState }
        assertEquals(
            listOf(JobState.New, JobState.Active, JobState.Completing, JobState.Completed),
            lazyStates,
            "Lazy child should reach Active after .start() is called"
        )

        // The lazy child must reach Active AFTER the eager child reaches Active
        // (the eager one starts immediately; the lazy one only after .start())
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val eagerActive = stateChanges.indexOfFirst { it.nodeId == "eager" && it.toState == JobState.Active }
        val lazyActive = stateChanges.indexOfFirst { it.nodeId == "lazy" && it.toState == JobState.Active }
        assertTrue(eagerActive < lazyActive,
            "Eager child must reach Active before the lazy child (which waits for .start())")

        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }

    // ── Intermediate: three wake-up methods ──────────────────────────

    @Test
    fun `intermediate - lazy coroutines can be woken by start, join, or await`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {by-start, by-join, by-await}
        assertEquals(4, collectNodeIds(timeline.tree).size)

        // All three reach Active (each via a different mechanism) then Completed
        assertNodeReachesFinalState(timeline, "by-start", JobState.Completed)
        assertNodeReachesFinalState(timeline, "by-join", JobState.Completed)
        assertNodeReachesFinalState(timeline, "by-await", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Each child must go through New → Active (not skip the lazy state)
        for (id in listOf("by-start", "by-join", "by-await")) {
            val changes = stateChangesForNode(timeline, id)
            assertEquals(JobState.New, changes.first().fromState,
                "$id must start from New (was created lazily)")
            assertEquals(JobState.Active, changes.first().toState,
                "$id must transition New → Active when woken")
        }

        // The async child uses BuilderType.Async (the Deferred case)
        val byAwait = timeline.tree.children.find { it.id == "by-await" }!!
        assertEquals(BuilderType.Async, byAwait.builder,
            "by-await must be an async (it's a Deferred that .await() wakes)")
    }

    // ── Advanced: forgotten-lazy deadlock + cancel escape ────────────

    @Test
    fun `advanced - forgotten lazy child blocks scope completion until explicitly cancelled`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {lazy-started, lazy-forgotten}
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // lazy-started follows the normal woken-by-start lifecycle
        assertNodeReachesFinalState(timeline, "lazy-started", JobState.Completed)

        // lazy-forgotten goes directly New → Cancelled (no Active, no body ran)
        val forgottenChanges = stateChangesForNode(timeline, "lazy-forgotten")
        assertEquals(1, forgottenChanges.size,
            "lazy-forgotten should have exactly one state change (New → Cancelled)")
        assertEquals(JobState.New, forgottenChanges.first().fromState)
        assertEquals(JobState.Cancelled, forgottenChanges.first().toState,
            "Cancelling a lazy Job in New should go directly to Cancelled (no Active, no Cancelling)")

        // The cancel must come from root (parent) and target the forgotten child
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "root" && it.targetNodeId == "lazy-forgotten" },
            "There must be a cancel from root to the forgotten lazy child")

        // Cancellation must happen BEFORE the scope completes (otherwise we'd deadlock)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val forgottenCancelled = stateChanges.indexOfFirst {
            it.nodeId == "lazy-forgotten" && it.toState == JobState.Cancelled
        }
        val rootCompleting = stateChanges.indexOfFirst {
            it.nodeId == "root" && it.toState == JobState.Completing
        }
        assertTrue(forgottenCancelled < rootCompleting,
            "Forgotten child must be Cancelled before scope can complete (otherwise scope hangs)")

        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }
}
