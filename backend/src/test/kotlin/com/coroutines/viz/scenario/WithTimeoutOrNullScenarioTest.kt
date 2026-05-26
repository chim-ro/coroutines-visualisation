package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class WithTimeoutOrNullScenarioTest {

    private val scenario = WithTimeoutOrNullScenario()

    // ── Beginner: timeout fires, block Cancelled internally, scope completes normally ──

    @Test
    fun `beginner - timeout cancels the block but coroutineScope continues normally (no exception)`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → timeout-block → slow-work
        assertEquals(3, collectNodeIds(timeline.tree).size)

        // Internal: slow-work and timeout-block both Cancelled
        assertNodeReachesFinalState(timeline, "slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "timeout-block", JobState.Cancelled)

        // Critical: the outer scope completes NORMALLY (no exception escaped withTimeoutOrNull)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // The timeout-block reaching Cancelled MUST come before root's Completing transition
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val blockCancelled = stateChanges.indexOfFirst { it.nodeId == "timeout-block" && it.toState == JobState.Cancelled }
        val rootCompleting = stateChanges.indexOfFirst { it.nodeId == "root" && it.toState == JobState.Completing }
        assertTrue(blockCancelled < rootCompleting,
            "withTimeoutOrNull's internal Cancellation must complete before the outer scope completes")

        // Cancellation must come from timeout-block to slow-work (timeout firing internally)
        val cancellations = timeline.events.filterIsInstance<CancellationEvent>()
        assertTrue(cancellations.any { it.sourceNodeId == "timeout-block" && it.targetNodeId == "slow-work" },
            "Timeout block must cancel its slow-work child")
    }

    // ── Intermediate: one succeeds (Completed), one times out (Cancelled), scope continues ──

    @Test
    fun `intermediate - one call completes, one times out, scope returns normally for both`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {fast-timeout → fast-work, slow-timeout → slow-work}
        assertEquals(5, collectNodeIds(timeline.tree).size)

        // Fast call: work AND block complete normally
        assertNodeReachesFinalState(timeline, "fast-work", JobState.Completed)
        assertNodeReachesFinalState(timeline, "fast-timeout", JobState.Completed)

        // Slow call: work AND block are Cancelled (timeout fired)
        assertNodeReachesFinalState(timeline, "slow-work", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "slow-timeout", JobState.Cancelled)

        // Outer scope completes normally — neither timeout call propagated an exception
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // The fast call must fully complete (block Completed) BEFORE the slow call's block becomes Active
        // (they're sequential)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val fastBlockCompleted = stateChanges.indexOfFirst {
            it.nodeId == "fast-timeout" && it.toState == JobState.Completed
        }
        val slowBlockActive = stateChanges.indexOfFirst {
            it.nodeId == "slow-timeout" && it.toState == JobState.Active
        }
        assertTrue(fastBlockCompleted < slowBlockActive,
            "Fast call must fully complete before the slow call starts (sequential)")
    }

    // ── Advanced: side-by-side — withTimeout vs withTimeoutOrNull internally identical ──

    @Test
    fun `advanced - withTimeout and withTimeoutOrNull both cancel internally, both outer scopes still complete`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees: LEFT (withTimeout) and RIGHT (withTimeoutOrNull)
        assertNotNull(timeline.secondTree, "Advanced level must have a secondTree")
        assertEquals("wt-scope", timeline.tree.id)
        assertEquals("or-scope", timeline.secondTree!!.id)

        // Both sides have scope → block → work = 3 nodes per side
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
