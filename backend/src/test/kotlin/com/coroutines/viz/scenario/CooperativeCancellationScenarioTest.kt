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

class CooperativeCancellationScenarioTest {

    private val scenario = CooperativeCancellationScenario()

    // ── Beginner: cooperative child detects cancellation and ends Cancelled ──

    @Test
    fun `beginner - cooperative child reaches Cancelled at a suspension point, scope completes`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(2, collectNodeIds(timeline.tree).size)

        // Cooperative child ends Cancelled; cancelling only one specific job does NOT
        // cancel the scope itself (job.cancel(), not scope cancel), so root completes.
        assertNodeReachesFinalState(timeline, "child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // The cancel signal (parent → child) must precede the child entering Cancelling.
        val cancels = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancels.any { it.sourceNodeId == "root" && it.targetNodeId == "child" },
            "There must be a cancellation signal from root to the child")
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val signalIdx = timeline.events.indexOfFirst { it is CancellationEvent && it.targetNodeId == "child" }
        val cancellingIdx = timeline.events.indexOfFirst {
            it is StateChangeEvent && it.nodeId == "child" && it.toState == JobState.Cancelling
        }
        assertTrue(signalIdx < cancellingIdx,
            "Child must enter Cancelling only AFTER the cancel signal (it checks at a suspension point)")
    }

    // ── Intermediate: non-cooperative child STILL ends Cancelled (the key bug-fix) ──

    @Test
    fun `intermediate - non-cooperative child ignores cancellation but still ends Cancelled, not Completed`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Dual-tree: cooperative (left) vs non-cooperative (right)
        assertNotNull(timeline.secondTree, "Intermediate is a side-by-side dual tree")
        assertEquals("coop-root", timeline.tree.id)
        assertEquals("noncoop-root", timeline.secondTree!!.id)

        // BOTH children end Cancelled — the whole point is that ignoring cancellation
        // does NOT let the non-cooperative child reach Completed.
        assertNodeReachesFinalState(timeline, "coop-child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "noncoop-child", JobState.Cancelled)

        // Non-cooperative child's Job flips to Cancelling immediately on cancel() —
        // long before it actually stops (its body keeps running).
        val noncoopChanges = stateChangesForNode(timeline, "noncoop-child")
        val states = listOf(noncoopChanges.first().fromState) + noncoopChanges.map { it.toState }
        assertEquals(
            listOf(JobState.New, JobState.Active, JobState.Cancelling, JobState.Cancelled),
            states,
            "Non-cooperative child: New → Active → Cancelling (immediate) → Cancelled (after body finishes)"
        )

        // The non-cooperative child reaches Cancelling BEFORE the cooperative child does
        // (Cancelling flips immediately on cancel(); cooperative only flips at its next check).
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val noncoopCancelling = stateChanges.indexOfFirst { it.nodeId == "noncoop-child" && it.toState == JobState.Cancelling }
        val coopCancelling = stateChanges.indexOfFirst { it.nodeId == "coop-child" && it.toState == JobState.Cancelling }
        assertTrue(noncoopCancelling < coopCancelling,
            "Non-cooperative child's Job flips to Cancelling immediately, before the cooperative one detects it")
    }

    // ── Advanced: all three strategies end Cancelled (including non-cooperative) ──

    @Test
    fun `advanced - ensureActive, isActive, and non-cooperative children all end Cancelled`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        assertEquals(4, collectNodeIds(timeline.tree).size) // root + 3 children

        // All three children end Cancelled — even the non-cooperative one (child-3).
        assertNodeReachesFinalState(timeline, "child-1", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-2", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "child-3", JobState.Cancelled)

        // child-3 (non-cooperative) is the critical case: it must NOT pass through Completing,
        // and its Cancelling must appear before it ends.
        val child3 = stateChangesForNode(timeline, "child-3")
        assertTrue(child3.none { it.toState == JobState.Completing },
            "Non-cooperative child must never reach Completing — cancel() wins")
        assertTrue(child3.none { it.toState == JobState.Completed },
            "Non-cooperative child must never reach Completed")
        assertEquals(JobState.Cancelled, child3.last().toState)

        // Parent uses cancelChildren() (not scope cancel), so the scope itself completes.
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }
}
