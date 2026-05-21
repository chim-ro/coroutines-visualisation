package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class ThreadsVsCoroutinesScenarioTest {

    private val scenario = ThreadsVsCoroutinesScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - side-by-side comparison with 2 tasks has correct tree and thread lanes`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)

        // Must have two trees and timeline visualization
        assertNotNull(timeline.secondTree, "Must have secondTree for comparison")
        assertEquals("timeline", timeline.visualizationMode)

        // Left tree: sync-root → {sync-task1, sync-task2}
        assertEquals(3, collectNodeIds(timeline.tree).size)
        // Right tree: cr-root → {cr-task1, cr-task2}
        assertEquals(3, collectNodeIds(timeline.secondTree!!).size)

        // Thread lanes
        assertNotNull(timeline.leftThreadLanes, "Must have left thread lanes")
        assertNotNull(timeline.rightThreadLanes, "Must have right thread lanes")
        assertEquals(3, timeline.leftThreadLanes!!.size, "Left: Main + Thread-1 + Thread-2")
        assertEquals(1, timeline.rightThreadLanes!!.size, "Right: only Main Thread (coroutines)")

        // All state transitions should go through Completing (Active → Completing → Completed)
        assertNoNoopStateChanges(timeline)
        val directCompletions = timeline.events.filterIsInstance<StateChangeEvent>()
            .filter { it.fromState == JobState.Active && it.toState == JobState.Completed }
        assertTrue(directCompletions.isEmpty(),
            "All completions should go through Completing state, not skip directly to Completed")

        assertTrue(timeline.totalDurationMs > 0, "Total duration should be positive")
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - 3 tasks show threads use 4 threads vs coroutines use 1`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)

        // Left has 4 lanes (Main + 3 worker threads), right has 1 (Main)
        assertEquals(4, timeline.leftThreadLanes!!.size)
        assertEquals(1, timeline.rightThreadLanes!!.size)

        // Coroutine tasks go through suspension
        val suspensions = timeline.events.filterIsInstance<StateChangeEvent>()
            .filter { it.nodeId.startsWith("cr-") && it.toState == JobState.Suspended }
        assertEquals(3, suspensions.size, "All 3 coroutine tasks should suspend")

        // All tasks complete on both sides
        assertNodeReachesFinalState(timeline, "sync-root", JobState.Completed)
        assertNodeReachesFinalState(timeline, "cr-root", JobState.Completed)
    }

    // ── Advanced: Complex Case (withContext IO) ──────────────────────

    @Test
    fun `advanced - 4 tasks with withContext IO uses 2 threads vs 5 for thread approach`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)

        // Left: 5 lanes (Main + 4 workers), Right: 2 lanes (Main + IO)
        assertEquals(5, timeline.leftThreadLanes!!.size)
        assertEquals(2, timeline.rightThreadLanes!!.size)

        // withContext(IO) should use BuilderType.CoroutineScope (consistent with DispatchersScenario)
        val crTask1 = timeline.secondTree!!.children.find { it.id == "cr-task1" }!!
        val withCtxIo = crTask1.children.find { it.id == "cr-task1-io" }!!
        assertEquals(BuilderType.CoroutineScope, withCtxIo.builder,
            "withContext(IO) should use BuilderType.CoroutineScope, not Launch")

        // IO thread lane should exist and have a segment
        val ioLane = timeline.rightThreadLanes!!.find { it.threadName == "IO Thread" }
        assertNotNull(ioLane, "Should have an IO Thread lane")
        assertTrue(ioLane.segments.isNotEmpty(), "IO Thread should have segments")

        // All roots complete
        assertNodeReachesFinalState(timeline, "sync-root", JobState.Completed)
        assertNodeReachesFinalState(timeline, "cr-root", JobState.Completed)
    }
}
