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

class SuspensionResumptionScenarioTest {

    private val scenario = SuspensionResumptionScenario()

    // ── Beginner: Happy Path ─────────────────────────────────────────

    @Test
    fun `beginner - single worker suspends and resumes correctly`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → worker
        assertEquals(2, collectNodeIds(timeline.tree).size)

        // Worker must go through: New → Active → Suspended → Active → Completing → Completed
        val workerChanges = stateChangesForNode(timeline, "worker")
        val stateSequence = listOf(workerChanges.first().fromState) +
                workerChanges.map { it.toState }
        assertEquals(
            listOf(JobState.New, JobState.Active, JobState.Suspended, JobState.Active, JobState.Completing, JobState.Completed),
            stateSequence,
            "Worker should follow full suspend/resume lifecycle"
        )

        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
    }

    // ── Intermediate: Failure/Edge Case ──────────────────────────────

    @Test
    fun `intermediate - fetcher suspends while processor and logger run on freed thread`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {fetcher, processor, logger}
        assertEquals(4, collectNodeIds(timeline.tree).size)

        // Fetcher suspends before processor and logger start
        val stateChanges = timeline.events.filterIsInstance<StateChangeEvent>()
        val fetcherSuspendIdx = stateChanges.indexOfFirst {
            it.nodeId == "fetcher" && it.toState == JobState.Suspended
        }
        val processorStartIdx = stateChanges.indexOfFirst {
            it.nodeId == "processor" && it.toState == JobState.Active
        }
        assertTrue(fetcherSuspendIdx < processorStartIdx,
            "Fetcher must suspend before processor starts (cooperative scheduling)")

        // All complete
        assertNodeReachesFinalState(timeline, "fetcher", JobState.Completed)
        assertNodeReachesFinalState(timeline, "processor", JobState.Completed)
        assertNodeReachesFinalState(timeline, "logger", JobState.Completed)
    }

    // ── Advanced: Complex Case ───────────────────────────────────────

    @Test
    fun `advanced - multiple suspend-resume cycles across 4 coroutines`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree: root → {fetcher, processor, logger, cache}
        assertEquals(5, collectNodeIds(timeline.tree).size)

        // Fetcher should suspend twice (two network calls)
        val fetcherChanges = stateChangesForNode(timeline, "fetcher")
        val suspendCount = fetcherChanges.count { it.toState == JobState.Suspended }
        assertEquals(2, suspendCount, "Fetcher should suspend exactly twice (two network calls)")

        // All nodes should reach Completed
        assertNodeReachesFinalState(timeline, "root", JobState.Completed)
        assertNodeReachesFinalState(timeline, "cache", JobState.Completed)
    }
}
