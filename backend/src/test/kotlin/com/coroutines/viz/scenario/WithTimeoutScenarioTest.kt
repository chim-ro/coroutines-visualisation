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

    // ── Advanced: Complex Case (nested timeouts) ─────────────────────

    @Test
    fun `advanced - nested withTimeout fires independently, outer timeout cancels remaining`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → timeout-scope → {fast-work, medium-work, slow-work → nested-timeout → very-slow-work}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(7, nodeIds.size)

        // Nested timeout fires first: very-slow-work and nested-timeout cancelled
        assertNodeReachesFinalState(timeline, "very-slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "nested-timeout", JobState.Cancelled)

        // Fast and medium complete before outer timeout
        assertNodeReachesFinalState(timeline, "fast-work", JobState.Completed)
        assertNodeReachesFinalState(timeline, "medium-work", JobState.Completed)

        // Outer timeout cancels slow-work and timeout-scope
        assertNodeReachesFinalState(timeline, "slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "timeout-scope", JobState.Cancelled)

        // Root still completes
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // Nested timeout cancellation fires before outer timeout cancellation
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertEquals(2, cancellations.size, "Should have 2 timeout cancellations (nested + outer)")
        val nestedIdx = cancellations.indexOfFirst { it.sourceNodeId == "nested-timeout" }
        val outerIdx = cancellations.indexOfFirst { it.sourceNodeId == "timeout-scope" }
        assertTrue(nestedIdx < outerIdx, "Nested timeout should fire before outer timeout")
    }
}
