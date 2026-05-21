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

class DispatchersScenarioTest {

    private val scenario = DispatchersScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - single launch on Default dispatcher completes normally`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → cpu-work
        assertEquals(2, collectNodeIds(timeline.tree).size)

        assertNodeReachesFinalState(timeline, "cpu-work", JobState.Completed)
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)

        // No exceptions or cancellations
        assertEquals(0, timeline.events.filterIsInstance<ExceptionEvent>().size)
        assertEquals(0, timeline.events.filterIsInstance<CancellationEvent>().size)
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - withContext switches dispatcher without creating new coroutine`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {cpu-work, io-work, switcher → with-context}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(5, nodeIds.size)

        // withContext should use CoroutineScope builder type (not Launch)
        val switcher = timeline.tree.children.find { it.id == "switcher" }!!
        val withCtx = switcher.children.find { it.id == "with-context" }!!
        assertEquals(BuilderType.CoroutineScope, withCtx.builder,
            "withContext should be modeled as CoroutineScope, not Launch")

        // withContext must complete before switcher completes
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val withCtxCompleted = stateChanges.indexOfFirst { it.nodeId == "with-context" && it.toState == JobState.Completed }
        val switcherCompleted = stateChanges.indexOfFirst { it.nodeId == "switcher" && it.toState == JobState.Completed }
        assertTrue(withCtxCompleted < switcherCompleted,
            "withContext must complete before its parent switcher")

        // All complete
        for (id in nodeIds) {
            assertNodeReachesFinalState(timeline, id, JobState.Completed)
        }
    }

    // ── Advanced: Complex Case (sequential withContext calls) ─────────

    @Test
    fun `advanced - sequential withContext calls on Default then IO with 4 children`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {cpu-work, io-work, ui-work, switcher → {ctx-default, ctx-io}}
        val nodeIds = collectNodeIds(timeline.tree)
        assertEquals(7, nodeIds.size)

        // Both withContext blocks use CoroutineScope builder
        val switcher = timeline.tree.children.find { it.id == "switcher" }!!
        assertEquals(2, switcher.children.size, "Switcher should have 2 withContext children")
        for (child in switcher.children) {
            assertEquals(BuilderType.CoroutineScope, child.builder)
        }

        // withContext(Default) must complete before withContext(IO) starts (sequential)
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val ctxDefaultCompleted = stateChanges.indexOfFirst { it.nodeId == "ctx-default" && it.toState == JobState.Completed }
        val ctxIoStarted = stateChanges.indexOfFirst { it.nodeId == "ctx-io" && it.toState == JobState.Active }
        assertTrue(ctxDefaultCompleted < ctxIoStarted,
            "withContext(Default) must complete before withContext(IO) starts (they are sequential)")

        // All nodes complete
        for (id in nodeIds) {
            assertNodeReachesFinalState(timeline, id, JobState.Completed)
        }

        assertAllNodesHaveEvents(timeline)
    }
}
