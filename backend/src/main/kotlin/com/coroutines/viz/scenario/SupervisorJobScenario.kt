package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class SupervisorJobScenario : Scenario {
    override val info = ScenarioInfo(
        id = "supervisor-job",
        name = "SupervisorJob",
        description = "Same exception but under SupervisorJob — only the failing child is cancelled, siblings continue.",
        category = "Exceptions"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "launch #2 (fails)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "async #3",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope starts — uses SupervisorJob"),
            StateChangeEvent(100, "Supervisor scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "async #3 starts", "child-3", JobState.New, JobState.Active),
            NarrativeEvent(800, "launch #2 throws the same exception as before..."),
            StateChangeEvent(1000, "launch #2 fails — enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1100, "launch #2 is Cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1300, "Exception propagates up to SupervisorJob", "child-2", "root", "RuntimeException: Something went wrong"),
            NarrativeEvent(1400, "SupervisorJob catches the exception — does NOT cancel siblings!"),
            NarrativeEvent(1600, "launch #1 and async #3 continue working normally..."),
            StateChangeEvent(2000, "launch #1 finishes its work", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2100, "launch #1 completed", "child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2300, "async #3 finishes its work", "child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(2400, "async #3 completed", "child-3", JobState.Completing, JobState.Completed),
            StateChangeEvent(2600, "Supervisor scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2700, "Supervisor scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2800, "SupervisorJob isolates failures — one child's crash doesn't affect siblings")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = supervisorScope {
    launch {  // launch #1
        delay(2000)
        println("Child 1 done")  // still completes!
    }

    launch {  // launch #2 (fails)
        delay(500)
        throw RuntimeException("Something went wrong")
    }

    async {   // async #3
        delay(2000)
        "result"  // still completes!
    }
    // SupervisorJob absorbs the exception — siblings survive
}
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
        val tree = CoroutineNode(
            id = "root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1 (fails)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "launch #2",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope starts — it uses a SupervisorJob internally"),
            StateChangeEvent(100, "Supervisor scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            NarrativeEvent(800, "launch #1 throws an exception..."),
            StateChangeEvent(1000, "launch #1 fails — enters Cancelling", "child-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1100, "launch #1 is Cancelled", "child-1", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1300, "Exception propagates up to SupervisorJob", "child-1", "root", "RuntimeException: Oops!"),
            NarrativeEvent(1400, "SupervisorJob absorbs the exception — does NOT cancel child #2!"),
            NarrativeEvent(1600, "launch #2 keeps running normally..."),
            StateChangeEvent(2000, "launch #2 finishes its work", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(2100, "launch #2 completed", "child-2", JobState.Completing, JobState.Completed),
            StateChangeEvent(2300, "Supervisor scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2400, "Supervisor scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2500, "One child failed, but the other survived — that's SupervisorJob!")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = supervisorScope {
    launch {  // launch #1 (fails)
        delay(500)
        throw RuntimeException("Oops!")
    }

    launch {  // launch #2
        delay(2000)
        println("Child 2 done")  // still completes!
    }
    // SupervisorJob absorbs the failure — sibling survives
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "launch #2 (fails)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "launch #3",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "scope",
                            displayName = "coroutineScope",
                            builder = BuilderType.CoroutineScope,
                            jobType = JobType.Job,
                            initialState = JobState.New,
                            children = listOf(
                                CoroutineNode(
                                    id = "inner-1",
                                    displayName = "launch inner-1",
                                    builder = BuilderType.Launch,
                                    jobType = JobType.Job,
                                    initialState = JobState.New
                                ),
                                CoroutineNode(
                                    id = "inner-2",
                                    displayName = "async inner-2",
                                    builder = BuilderType.Async,
                                    jobType = JobType.Job,
                                    initialState = JobState.New
                                )
                            )
                        )
                    )
                ),
                CoroutineNode(
                    id = "child-4",
                    displayName = "async #4",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope starts with SupervisorJob — complex hierarchy ahead"),
            StateChangeEvent(100, "Supervisor scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(200, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #3 starts", "child-3", JobState.New, JobState.Active),
            StateChangeEvent(500, "async #4 starts", "child-4", JobState.New, JobState.Active),
            StateChangeEvent(600, "coroutineScope inside launch #3 becomes Active", "scope", JobState.New, JobState.Active),
            StateChangeEvent(700, "inner-1 starts inside nested scope", "inner-1", JobState.New, JobState.Active),
            StateChangeEvent(800, "inner-2 starts inside nested scope", "inner-2", JobState.New, JobState.Active),
            NarrativeEvent(1000, "All coroutines are running — now launch #2 throws an exception..."),
            StateChangeEvent(1200, "launch #2 fails — enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1300, "launch #2 is Cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1500, "Exception propagates up to SupervisorJob", "child-2", "root", "RuntimeException: Something went wrong"),
            NarrativeEvent(1600, "SupervisorJob catches the exception — siblings and their children are NOT affected!"),
            NarrativeEvent(1800, "launch #1, launch #3 (with nested scope), and async #4 all keep running..."),
            StateChangeEvent(2000, "launch #1 finishes its work", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2100, "launch #1 completed", "child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2300, "inner-1 finishes inside nested scope", "inner-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2400, "inner-1 completed", "inner-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2500, "inner-2 finishes inside nested scope", "inner-2", JobState.Active, JobState.Completing),
            StateChangeEvent(2600, "inner-2 completed", "inner-2", JobState.Completing, JobState.Completed),
            StateChangeEvent(2700, "Nested coroutineScope completing", "scope", JobState.Active, JobState.Completing),
            StateChangeEvent(2800, "Nested coroutineScope completed", "scope", JobState.Completing, JobState.Completed),
            StateChangeEvent(2900, "launch #3 finishing", "child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(3000, "launch #3 completed", "child-3", JobState.Completing, JobState.Completed),
            StateChangeEvent(3100, "async #4 finishes its work", "child-4", JobState.Active, JobState.Completing),
            StateChangeEvent(3200, "async #4 completed", "child-4", JobState.Completing, JobState.Completed),
            StateChangeEvent(3400, "Supervisor scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(3500, "Supervisor scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3600, "SupervisorJob isolated the failure — even deeply nested children in sibling branches survived")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = supervisorScope {
    launch {  // launch #1
        delay(2000)
        println("Child 1 done")
    }

    launch {  // launch #2 (fails)
        delay(500)
        throw RuntimeException("Something went wrong")
    }

    launch {  // launch #3
        coroutineScope {
            launch {  // inner-1
                delay(2000)
                println("Inner 1 done")
            }
            async {   // inner-2
                delay(2000)
                "inner result"
            }
        }
        println("Child 3 done")
    }

    async {   // async #4
        delay(3000)
        "result"
    }
    // SupervisorJob absorbs child-2's failure
    // All other children — including nested ones — complete normally
}
            """.trimIndent()
        )
    }
}
