package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import com.coroutines.viz.scenario.TimelineTestHelper.assertNodeReachesFinalState
import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import com.coroutines.viz.scenario.TimelineTestHelper.assertNoNoopStateChanges
import com.coroutines.viz.scenario.TimelineTestHelper.assertStateTransitionConsistency
import com.coroutines.viz.scenario.TimelineTestHelper.collectNodeIds
import kotlin.test.*

class CoroutineExceptionHandlerScenarioTest {

    private val scenario = CoroutineExceptionHandlerScenario()

    // ── Beginner: one failing launch → exactly one CEH invocation ──────

    @Test
    fun `beginner - failing launch under supervisorScope delivers exception to CEH`() {
        val timeline = scenario.buildTimeline("beginner")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Two trees: scope (LEFT) + CEH node (RIGHT)
        assertNotNull(timeline.secondTree, "CEH scenario needs a secondTree for the handler node")
        assertEquals("scope", timeline.tree.id)
        assertEquals("ceh", timeline.secondTree!!.id)

        // Scope must be supervisor-flavored (CEH only fires reliably under SupervisorJob)
        assertEquals(BuilderType.SupervisorScope, timeline.tree.builder)
        assertEquals(JobType.SupervisorJob, timeline.tree.jobType)

        // Failing launch ends Cancelled; scope completes normally (supervisor isolates)
        assertNodeReachesFinalState(timeline, "failing", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "scope", JobState.Completed)

        // Exactly one ExceptionEvent should target the CEH
        val exceptionsToCeh = timeline.events.filterIsInstance<ExceptionEvent>().filter { it.targetNodeId == "ceh" }
        assertEquals(1, exceptionsToCeh.size,
            "Beginner: exactly one exception must reach the CEH (the failing launch)")
        assertEquals("failing", exceptionsToCeh.first().sourceNodeId)
    }

    // ── Intermediate: launch's exception → CEH, async's exception is lost ──

    @Test
    fun `intermediate - launch exception reaches CEH but async exception does not`() {
        val timeline = scenario.buildTimeline("intermediate")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Both children fail (Cancelled), scope completes (supervisor)
        assertNodeReachesFinalState(timeline, "launch-fails", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "async-fails", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "scope", JobState.Completed)

        val exceptionsToCeh = timeline.events.filterIsInstance<ExceptionEvent>().filter { it.targetNodeId == "ceh" }

        // The launch's exception MUST go to CEH
        assertTrue(exceptionsToCeh.any { it.sourceNodeId == "launch-fails" },
            "launch's exception must be delivered to CEH")

        // The async's exception MUST NOT go to CEH (it's held in the Deferred)
        assertTrue(exceptionsToCeh.none { it.sourceNodeId == "async-fails" },
            "async's exception MUST NOT go to CEH — it lives in the Deferred until .await() is called")
    }

    // ── Advanced: 5 children — CEH sees launch root + nested-parent's propagation ──

    @Test
    fun `advanced - CEH sees both root launch failure AND nested-failure that propagated up to a direct supervisor child`() {
        val timeline = scenario.buildTimeline("advanced")

        assertTimelineIsValid(timeline)
        assertNoNoopStateChanges(timeline)
        assertStateTransitionConsistency(timeline)

        // Tree must have all 5 children + nest-parent's nested child = 6 + scope = 7 nodes
        assertEquals(7, collectNodeIds(timeline.tree).size)

        // ok-launch succeeds; the rest end Cancelled
        assertNodeReachesFinalState(timeline, "ok-launch", JobState.Completed)
        assertNodeReachesFinalState(timeline, "bad-launch", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "nest-parent", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "nest-child", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "async-awaited", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "async-orphan", JobState.Cancelled)
        assertNodeReachesFinalState(timeline, "scope", JobState.Completed)

        val exceptionsToCeh = timeline.events.filterIsInstance<ExceptionEvent>().filter { it.targetNodeId == "ceh" }

        // CEH must receive: bad-launch's exception + nest-parent's exception (originally from nest-child)
        val cehSources = exceptionsToCeh.map { it.sourceNodeId }.toSet()
        assertEquals(setOf("bad-launch", "nest-parent"), cehSources,
            "CEH must receive exactly two exceptions: bad-launch (direct) and nest-parent (after nested-child propagated)")

        // Neither async should reach CEH
        assertTrue(cehSources.none { it.startsWith("async") },
            "Neither async child's exception should reach CEH (both are in Deferreds)")

        // The nested-child's exception should propagate to nest-parent (a normal upward propagation),
        // NOT directly to CEH
        val exceptionsFromNestChild = timeline.events.filterIsInstance<ExceptionEvent>().filter { it.sourceNodeId == "nest-child" }
        assertTrue(exceptionsFromNestChild.any { it.targetNodeId == "nest-parent" },
            "nest-child's exception must propagate to nest-parent (its actual structural parent)")
        assertTrue(exceptionsFromNestChild.none { it.targetNodeId == "ceh" },
            "nest-child's exception must NOT go directly to CEH — it propagates to its parent launch first")
    }
}
