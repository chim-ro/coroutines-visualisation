package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ScopeComparisonScenario : Scenario {
    override val info = ScenarioInfo(
        id = "scope-comparison",
        name = "Scope Comparison",
        description = "Side-by-side: coroutineScope vs supervisorScope with the same exception.",
        category = "Comparison"
    )

    override fun buildTimeline(): EventTimeline {
        // Left tree: coroutineScope
        val tree1 = CoroutineNode(
            id = "cs-root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "cs-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cs-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cs-child-3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        // Right tree: supervisorScope
        val tree2 = CoroutineNode(
            id = "ss-root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "ss-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "ss-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "ss-child-3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Two identical structures — but different scope types"),
            // Both scopes start
            StateChangeEvent(100, "coroutineScope starts", "cs-root", JobState.New, JobState.Active),
            StateChangeEvent(150, "supervisorScope starts", "ss-root", JobState.New, JobState.Active),
            // All children start
            StateChangeEvent(300, "CS: launch #1 starts", "cs-child-1", JobState.New, JobState.Active),
            StateChangeEvent(350, "SS: launch #1 starts", "ss-child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "CS: launch #2 starts", "cs-child-2", JobState.New, JobState.Active),
            StateChangeEvent(450, "SS: launch #2 starts", "ss-child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "CS: launch #3 starts", "cs-child-3", JobState.New, JobState.Active),
            StateChangeEvent(550, "SS: launch #3 starts", "ss-child-3", JobState.New, JobState.Active),
            NarrativeEvent(800, "Both launch #2 throw the same exception..."),
            // Both #2 fail
            StateChangeEvent(1000, "CS: launch #2 fails", "cs-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1050, "SS: launch #2 fails", "ss-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1100, "CS: launch #2 cancelled", "cs-child-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1150, "SS: launch #2 cancelled", "ss-child-2", JobState.Cancelling, JobState.Cancelled),
            // Exceptions propagate
            ExceptionEvent(1300, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            ExceptionEvent(1350, "SS: exception propagates to supervisorScope", "ss-child-2", "ss-root", "RuntimeException"),
            // coroutineScope cancels siblings
            NarrativeEvent(1500, "coroutineScope cancels all siblings — supervisorScope does NOT"),
            StateChangeEvent(1600, "CS: scope enters Cancelling", "cs-root", JobState.Active, JobState.Cancelling),
            CancellationEvent(1700, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            StateChangeEvent(1800, "CS: launch #1 cancelling", "cs-child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(1850, "CS: cancelling launch #3", "cs-root", "cs-child-3"),
            StateChangeEvent(1900, "CS: launch #3 cancelling", "cs-child-3", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2000, "CS: launch #1 cancelled", "cs-child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2100, "CS: launch #3 cancelled", "cs-child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2200, "CS: scope cancelled", "cs-root", JobState.Cancelling, JobState.Cancelled),
            // supervisorScope siblings continue
            NarrativeEvent(2300, "SS siblings continue normally..."),
            StateChangeEvent(2500, "SS: launch #1 completing", "ss-child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2600, "SS: launch #1 completed", "ss-child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2700, "SS: launch #3 completing", "ss-child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(2800, "SS: launch #3 completed", "ss-child-3", JobState.Completing, JobState.Completed),
            StateChangeEvent(2900, "SS: supervisor scope completing", "ss-root", JobState.Active, JobState.Completing),
            StateChangeEvent(3000, "SS: supervisor scope completed", "ss-root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3100, "coroutineScope: all-or-nothing | supervisorScope: independent children")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope — all-or-nothing
coroutineScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
    launch { delay(2000); println("#3 done") }
}
// Exception cancels ALL siblings, scope re-throws

// RIGHT: supervisorScope — independent children
supervisorScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
    launch { delay(2000); println("#3 done") }
}
// Only the failing child is cancelled, siblings complete
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> buildTimeline()
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree1 = CoroutineNode(
            id = "cs-root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "cs-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cs-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val tree2 = CoroutineNode(
            id = "ss-root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "ss-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "ss-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Two scopes, two children each — same exception, different behavior"),
            // Both scopes start
            StateChangeEvent(100, "coroutineScope starts", "cs-root", JobState.New, JobState.Active),
            StateChangeEvent(150, "supervisorScope starts", "ss-root", JobState.New, JobState.Active),
            // All children start
            StateChangeEvent(300, "CS: launch #1 starts", "cs-child-1", JobState.New, JobState.Active),
            StateChangeEvent(350, "SS: launch #1 starts", "ss-child-1", JobState.New, JobState.Active),
            StateChangeEvent(500, "CS: launch #2 starts", "cs-child-2", JobState.New, JobState.Active),
            StateChangeEvent(550, "SS: launch #2 starts", "ss-child-2", JobState.New, JobState.Active),
            NarrativeEvent(800, "Both launch #2 throw an exception..."),
            // Both #2 fail
            StateChangeEvent(1000, "CS: launch #2 fails", "cs-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1050, "SS: launch #2 fails", "ss-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1100, "CS: launch #2 cancelled", "cs-child-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1150, "SS: launch #2 cancelled", "ss-child-2", JobState.Cancelling, JobState.Cancelled),
            // Exceptions propagate
            ExceptionEvent(1300, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            ExceptionEvent(1350, "SS: exception propagates to supervisorScope", "ss-child-2", "ss-root", "RuntimeException"),
            // coroutineScope cancels sibling
            NarrativeEvent(1500, "coroutineScope cancels sibling — supervisorScope does NOT"),
            StateChangeEvent(1600, "CS: scope enters Cancelling", "cs-root", JobState.Active, JobState.Cancelling),
            CancellationEvent(1700, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            StateChangeEvent(1800, "CS: launch #1 cancelling", "cs-child-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1900, "CS: launch #1 cancelled", "cs-child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2000, "CS: scope cancelled", "cs-root", JobState.Cancelling, JobState.Cancelled),
            // supervisorScope sibling continues
            NarrativeEvent(2100, "SS: launch #1 continues normally..."),
            StateChangeEvent(2300, "SS: launch #1 completing", "ss-child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2400, "SS: launch #1 completed", "ss-child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2500, "SS: supervisor scope completing", "ss-root", JobState.Active, JobState.Completing),
            StateChangeEvent(2600, "SS: supervisor scope completed", "ss-root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2700, "coroutineScope: cancelled everything | supervisorScope: only the failing child stopped")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope
coroutineScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
}
// Exception cancels sibling, scope re-throws

// RIGHT: supervisorScope
supervisorScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
}
// Only the failing child is cancelled
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree1 = CoroutineNode(
            id = "cs-root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "cs-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(id = "cs-grandchild-1a", displayName = "launch #1a", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                        CoroutineNode(id = "cs-grandchild-1b", displayName = "launch #1b", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                    )
                ),
                CoroutineNode(id = "cs-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cs-child-3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val tree2 = CoroutineNode(
            id = "ss-root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "ss-child-1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(id = "ss-grandchild-1a", displayName = "launch #1a", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                        CoroutineNode(id = "ss-grandchild-1b", displayName = "launch #1b", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                    )
                ),
                CoroutineNode(id = "ss-child-2", displayName = "launch #2 (fails)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "ss-child-3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Nested trees — exception propagation through multiple levels"),
            // Both scopes start
            StateChangeEvent(100, "coroutineScope starts", "cs-root", JobState.New, JobState.Active),
            StateChangeEvent(150, "supervisorScope starts", "ss-root", JobState.New, JobState.Active),
            // All children start
            StateChangeEvent(300, "CS: launch #1 starts", "cs-child-1", JobState.New, JobState.Active),
            StateChangeEvent(350, "SS: launch #1 starts", "ss-child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "CS: launch #2 starts", "cs-child-2", JobState.New, JobState.Active),
            StateChangeEvent(450, "SS: launch #2 starts", "ss-child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "CS: launch #3 starts", "cs-child-3", JobState.New, JobState.Active),
            StateChangeEvent(550, "SS: launch #3 starts", "ss-child-3", JobState.New, JobState.Active),
            // Grandchildren start
            StateChangeEvent(700, "CS: launch #1a starts", "cs-grandchild-1a", JobState.New, JobState.Active),
            StateChangeEvent(750, "SS: launch #1a starts", "ss-grandchild-1a", JobState.New, JobState.Active),
            StateChangeEvent(800, "CS: launch #1b starts", "cs-grandchild-1b", JobState.New, JobState.Active),
            StateChangeEvent(850, "SS: launch #1b starts", "ss-grandchild-1b", JobState.New, JobState.Active),
            NarrativeEvent(1000, "All coroutines running — now launch #2 throws on both sides..."),
            // Both #2 fail
            StateChangeEvent(1200, "CS: launch #2 fails", "cs-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1250, "SS: launch #2 fails", "ss-child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1300, "CS: launch #2 cancelled", "cs-child-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1350, "SS: launch #2 cancelled", "ss-child-2", JobState.Cancelling, JobState.Cancelled),
            // Exceptions propagate
            ExceptionEvent(1500, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            ExceptionEvent(1550, "SS: exception propagates to supervisorScope", "ss-child-2", "ss-root", "RuntimeException"),
            // coroutineScope cancels EVERYTHING — siblings and their children
            NarrativeEvent(1700, "coroutineScope cancels the entire tree — including grandchildren!"),
            StateChangeEvent(1800, "CS: scope enters Cancelling", "cs-root", JobState.Active, JobState.Cancelling),
            CancellationEvent(1900, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            StateChangeEvent(1950, "CS: launch #1 cancelling", "cs-child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(2000, "CS: cancelling launch #1a", "cs-child-1", "cs-grandchild-1a"),
            StateChangeEvent(2050, "CS: launch #1a cancelling", "cs-grandchild-1a", JobState.Active, JobState.Cancelling),
            CancellationEvent(2100, "CS: cancelling launch #1b", "cs-child-1", "cs-grandchild-1b"),
            StateChangeEvent(2150, "CS: launch #1b cancelling", "cs-grandchild-1b", JobState.Active, JobState.Cancelling),
            CancellationEvent(2200, "CS: cancelling launch #3", "cs-root", "cs-child-3"),
            StateChangeEvent(2250, "CS: launch #3 cancelling", "cs-child-3", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2400, "CS: launch #1a cancelled", "cs-grandchild-1a", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2450, "CS: launch #1b cancelled", "cs-grandchild-1b", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2500, "CS: launch #1 cancelled", "cs-child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2550, "CS: launch #3 cancelled", "cs-child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2650, "CS: scope cancelled", "cs-root", JobState.Cancelling, JobState.Cancelled),
            // supervisorScope — siblings and grandchildren all continue
            NarrativeEvent(2700, "SS: siblings and their grandchildren continue unaffected"),
            StateChangeEvent(2900, "SS: launch #1a completing", "ss-grandchild-1a", JobState.Active, JobState.Completing),
            StateChangeEvent(2950, "SS: launch #1a completed", "ss-grandchild-1a", JobState.Completing, JobState.Completed),
            StateChangeEvent(3000, "SS: launch #1b completing", "ss-grandchild-1b", JobState.Active, JobState.Completing),
            StateChangeEvent(3050, "SS: launch #1b completed", "ss-grandchild-1b", JobState.Completing, JobState.Completed),
            StateChangeEvent(3150, "SS: launch #1 completing", "ss-child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(3200, "SS: launch #1 completed", "ss-child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(3300, "SS: launch #3 completing", "ss-child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(3350, "SS: launch #3 completed", "ss-child-3", JobState.Completing, JobState.Completed),
            StateChangeEvent(3450, "SS: supervisor scope completing", "ss-root", JobState.Active, JobState.Completing),
            StateChangeEvent(3500, "SS: supervisor scope completed", "ss-root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3600, "coroutineScope: entire tree destroyed | supervisorScope: only the failing child stopped, nested coroutines completed")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope — cascading cancellation
coroutineScope {
    launch {                            // #1
        launch { delay(3000); println("#1a done") }
        launch { delay(3000); println("#1b done") }
        delay(3000); println("#1 done")
    }
    launch { throw RuntimeException() } // #2 — fails
    launch { delay(3000); println("#3 done") }
}
// Exception propagates up, cancels ALL children + grandchildren

// RIGHT: supervisorScope — isolated failure
supervisorScope {
    launch {                            // #1
        launch { delay(3000); println("#1a done") }
        launch { delay(3000); println("#1b done") }
        delay(3000); println("#1 done")
    }
    launch { throw RuntimeException() } // #2 — fails
    launch { delay(3000); println("#3 done") }
}
// Only #2 fails — #1, #1a, #1b, and #3 all complete
            """.trimIndent()
        )
    }
}
