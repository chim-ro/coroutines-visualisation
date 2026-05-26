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

class WithTimeoutScenarioTest {

    private val scenario = WithTimeoutScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - slow child cancelled by timeout, root completes normally`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → timeout-scope → slow-work
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // Slow work and timeout scope cancelled, root completes
        assertNodeReachesFinalState(timeline, "slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "timeout-scope", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Timeout cancellation event (downward from scope to child)
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.isNotEmpty(), "Should have timeout CancellationEvent")
        assertTrue(
            cancellations.any { it.sourceNodeId == "timeout-scope" && it.targetNodeId == "slow-work" },
            "Should have cancellation from timeout-scope to slow-work"
        )
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - fast async completes before timeout, slow launch gets cancelled`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → timeout-scope → {slow-work, fast-work}
        assertEquals(4, collectNodeIds(timeline.tree).size)

        // Fast work completes before timeout
        assertNodeReachesFinalState(timeline, "fast-work", JobState.Completed)

        // Slow work and timeout scope cancelled
        assertNodeReachesFinalState(timeline, "slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "timeout-scope", JobState.Cancelled)

        // Root completes (timeout is contained)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Fast work must complete before slow work gets cancelled
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val fastCompleted = stateChanges.indexOfFirst { it.nodeId == "fast-work" && it.toState == JobState.Completed }
        val slowCancelled = stateChanges.indexOfFirst { it.nodeId == "slow-work" && it.toState == JobState.Cancelled }
        assertTrue(fastCompleted < slowCancelled, "Fast work should complete before slow work is cancelled")
    }

    // ── Advanced: side-by-side withTimeout vs withTimeoutOrNull ──────

    @Test
    fun `advanced - withTimeout and withTimeoutOrNull both cancel internally, both outer scopes still complete`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees: LEFT (withTimeout) + RIGHT (withTimeoutOrNull)
        assertNotNull(timeline.secondTree, "Advanced level must have a secondTree (side-by-side)")
        assertEquals("wt-scope", timeline.tree.id)
        assertEquals("or-scope", timeline.secondTree!!.id)

        // 3 nodes per side (outer scope + timeout block + slow work)
        assertEquals(3, collectNodeIds(timeline.tree).size)
        assertEquals(3, collectNodeIds(timeline.secondTree!!).size)

        // Both internal Jobs end Cancelled (timeout fired on both)
        assertNodeReachesFinalState(timeline, "wt-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "wt-block", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "or-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "or-block", JobState.Cancelled)

        // CRITICAL: both outer scopes still complete normally
        // (LEFT because the caller's try/catch absorbed the exception;
        //  RIGHT because withTimeoutOrNull doesn't throw in the first place)
        assertNodeReachesFinalState(timeline, "wt-scope", JobState.Completed)
        assertNodeReachesFinalState(timeline, "or-scope", JobState.Completed)

        // Each timeout block must cancel its own child
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "wt-block" && it.targetNodeId == "wt-work" },
            "LEFT: withTimeout block must cancel its work child")
        assertTrue(cancellations.any { it.sourceNodeId == "or-block" && it.targetNodeId == "or-work" },
            "RIGHT: withTimeoutOrNull block must cancel its work child")
    }
}
