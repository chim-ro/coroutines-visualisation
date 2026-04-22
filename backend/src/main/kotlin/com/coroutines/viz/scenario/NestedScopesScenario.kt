package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class NestedScopesScenario : Scenario {
    override val info = ScenarioInfo(
        id = "nested-scopes",
        name = "Nested Scopes",
        description = "Deep tree (4 levels) with mixed scope types showing how structured concurrency composes.",
        category = "Advanced"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "supervisor",
                    displayName = "supervisorScope",
                    builder = BuilderType.SupervisorScope,
                    jobType = JobType.SupervisorJob,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "branch-a",
                            displayName = "launch A",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New,
                            children = listOf(
                                CoroutineNode(id = "a1", displayName = "async A1", builder = BuilderType.Async, jobType = JobType.Job, initialState = JobState.New),
                                CoroutineNode(id = "a2", displayName = "async A2", builder = BuilderType.Async, jobType = JobType.Job, initialState = JobState.New)
                            )
                        ),
                        CoroutineNode(
                            id = "branch-b",
                            displayName = "launch B (fails)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New,
                            children = listOf(
                                CoroutineNode(id = "b1", displayName = "launch B1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                            )
                        ),
                        CoroutineNode(
                            id = "branch-c",
                            displayName = "launch C",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Deep nested structure: runBlocking > supervisorScope > 3 branches"),
            StateChangeEvent(100, "runBlocking starts", "root", JobState.New, JobState.Active),
            StateChangeEvent(200, "supervisorScope starts", "supervisor", JobState.New, JobState.Active),
            StateChangeEvent(400, "Launch A starts", "branch-a", JobState.New, JobState.Active),
            StateChangeEvent(500, "Launch B starts", "branch-b", JobState.New, JobState.Active),
            StateChangeEvent(600, "Launch C starts", "branch-c", JobState.New, JobState.Active),
            StateChangeEvent(700, "Async A1 starts", "a1", JobState.New, JobState.Active),
            StateChangeEvent(800, "Async A2 starts", "a2", JobState.New, JobState.Active),
            StateChangeEvent(900, "Launch B1 starts", "b1", JobState.New, JobState.Active),
            NarrativeEvent(1100, "Branch B encounters an error — exception propagates within B's subtree"),
            StateChangeEvent(1300, "B1 fails", "b1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1400, "B1 cancelled", "b1", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1500, "Exception from B1 propagates up to launch B", "b1", "branch-b", "IOException"),
            StateChangeEvent(1600, "Launch B enters Cancelling", "branch-b", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1700, "Launch B cancelled", "branch-b", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1800, "Exception reaches supervisorScope", "branch-b", "supervisor", "IOException"),
            NarrativeEvent(1900, "SupervisorJob stops the exception — branches A and C are safe!"),
            StateChangeEvent(2200, "Async A1 completes work", "a1", JobState.Active, JobState.Completing),
            StateChangeEvent(2300, "Async A1 completed", "a1", JobState.Completing, JobState.Completed),
            StateChangeEvent(2400, "Async A2 completes work", "a2", JobState.Active, JobState.Completing),
            StateChangeEvent(2500, "Async A2 completed", "a2", JobState.Completing, JobState.Completed),
            StateChangeEvent(2600, "Launch A completing", "branch-a", JobState.Active, JobState.Completing),
            StateChangeEvent(2700, "Launch A completed", "branch-a", JobState.Completing, JobState.Completed),
            StateChangeEvent(2800, "Launch C completing", "branch-c", JobState.Active, JobState.Completing),
            StateChangeEvent(2900, "Launch C completed", "branch-c", JobState.Completing, JobState.Completed),
            StateChangeEvent(3000, "Supervisor scope completing", "supervisor", JobState.Active, JobState.Completing),
            StateChangeEvent(3100, "Supervisor scope completed", "supervisor", JobState.Completing, JobState.Completed),
            StateChangeEvent(3200, "runBlocking completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(3300, "runBlocking completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3400, "SupervisorJob at level 2 contained the failure — siblings completed normally")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    supervisorScope {
        launch {                        // branch A
            val a1 = async { "result1" }
            val a2 = async { "result2" }
            println(a1.await() + a2.await())
        }

        launch {                        // branch B (fails)
            launch {                    // B1
                throw IOException("Network error")
            }
        }

        launch {                        // branch C
            delay(1000)
            println("C completed")      // still completes!
        }
    }
    // supervisorScope contains B's failure — A and C finish
}
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()

        "beginner" -> {
            val tree = CoroutineNode(
                id = "root",
                displayName = "runBlocking",
                builder = BuilderType.RunBlocking,
                jobType = JobType.Job,
                initialState = JobState.New,
                children = listOf(
                    CoroutineNode(
                        id = "supervisor",
                        displayName = "supervisorScope",
                        builder = BuilderType.SupervisorScope,
                        jobType = JobType.SupervisorJob,
                        initialState = JobState.New,
                        children = listOf(
                            CoroutineNode(
                                id = "child-a",
                                displayName = "launch A",
                                builder = BuilderType.Launch,
                                jobType = JobType.Job,
                                initialState = JobState.New
                            ),
                            CoroutineNode(
                                id = "child-b",
                                displayName = "launch B (fails)",
                                builder = BuilderType.Launch,
                                jobType = JobType.Job,
                                initialState = JobState.New
                            )
                        )
                    )
                )
            )

            val events = listOf(
                NarrativeEvent(0, "Simple nesting: runBlocking > supervisorScope > 2 children"),
                StateChangeEvent(100, "runBlocking starts", "root", JobState.New, JobState.Active),
                StateChangeEvent(300, "supervisorScope starts", "supervisor", JobState.New, JobState.Active),
                StateChangeEvent(500, "Launch A starts", "child-a", JobState.New, JobState.Active),
                StateChangeEvent(700, "Launch B starts", "child-b", JobState.New, JobState.Active),
                NarrativeEvent(1000, "Launch B throws an exception"),
                StateChangeEvent(1100, "Launch B enters Cancelling", "child-b", JobState.Active, JobState.Cancelling),
                StateChangeEvent(1200, "Launch B cancelled", "child-b", JobState.Cancelling, JobState.Cancelled),
                ExceptionEvent(1400, "Exception from B reaches supervisorScope", "child-b", "supervisor", "IOException"),
                NarrativeEvent(1500, "SupervisorJob absorbs the exception — child A is not affected"),
                StateChangeEvent(1800, "Launch A completing", "child-a", JobState.Active, JobState.Completing),
                StateChangeEvent(1900, "Launch A completed", "child-a", JobState.Completing, JobState.Completed),
                StateChangeEvent(2100, "supervisorScope completing", "supervisor", JobState.Active, JobState.Completing),
                StateChangeEvent(2200, "supervisorScope completed", "supervisor", JobState.Completing, JobState.Completed),
                StateChangeEvent(2400, "runBlocking completing", "root", JobState.Active, JobState.Completing),
                StateChangeEvent(2500, "runBlocking completed", "root", JobState.Completing, JobState.Completed),
                NarrativeEvent(2600, "SupervisorJob contained the failure — the surviving child completed normally")
            )

            EventTimeline(
                scenarioName = info.name,
                tree = tree,
                events = events,
                kotlinCode = """
fun main() = runBlocking {
    supervisorScope {
        launch {                    // child A
            delay(1000)
            println("A completed")  // survives!
        }

        launch {                    // child B (fails)
            throw IOException("Network error")
        }
    }
}
                """.trimIndent()
            )
        }

        "advanced" -> {
            val tree = CoroutineNode(
                id = "root",
                displayName = "runBlocking",
                builder = BuilderType.RunBlocking,
                jobType = JobType.Job,
                initialState = JobState.New,
                children = listOf(
                    CoroutineNode(
                        id = "supervisor",
                        displayName = "supervisorScope",
                        builder = BuilderType.SupervisorScope,
                        jobType = JobType.SupervisorJob,
                        initialState = JobState.New,
                        children = listOf(
                            CoroutineNode(
                                id = "branch-a",
                                displayName = "launch A",
                                builder = BuilderType.Launch,
                                jobType = JobType.Job,
                                initialState = JobState.New,
                                children = listOf(
                                    CoroutineNode(
                                        id = "scope-a",
                                        displayName = "coroutineScope",
                                        builder = BuilderType.CoroutineScope,
                                        jobType = JobType.Job,
                                        initialState = JobState.New,
                                        children = listOf(
                                            CoroutineNode(id = "a1", displayName = "async A1", builder = BuilderType.Async, jobType = JobType.Job, initialState = JobState.New),
                                            CoroutineNode(id = "a2", displayName = "async A2", builder = BuilderType.Async, jobType = JobType.Job, initialState = JobState.New)
                                        )
                                    )
                                )
                            ),
                            CoroutineNode(
                                id = "branch-b",
                                displayName = "launch B (fails)",
                                builder = BuilderType.Launch,
                                jobType = JobType.Job,
                                initialState = JobState.New,
                                children = listOf(
                                    CoroutineNode(id = "b1", displayName = "launch B1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                                )
                            ),
                            CoroutineNode(
                                id = "branch-c",
                                displayName = "launch C",
                                builder = BuilderType.Launch,
                                jobType = JobType.Job,
                                initialState = JobState.New,
                                children = listOf(
                                    CoroutineNode(id = "c1", displayName = "launch C1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                                )
                            )
                        )
                    )
                )
            )

            val events = listOf(
                NarrativeEvent(0, "5-level tree: runBlocking > supervisorScope > 3 branches with mixed scope types"),
                StateChangeEvent(100, "runBlocking starts", "root", JobState.New, JobState.Active),
                StateChangeEvent(200, "supervisorScope starts", "supervisor", JobState.New, JobState.Active),
                StateChangeEvent(350, "Launch A starts", "branch-a", JobState.New, JobState.Active),
                StateChangeEvent(450, "Launch B starts", "branch-b", JobState.New, JobState.Active),
                StateChangeEvent(550, "Launch C starts", "branch-c", JobState.New, JobState.Active),
                StateChangeEvent(650, "coroutineScope starts inside A", "scope-a", JobState.New, JobState.Active),
                StateChangeEvent(750, "Async A1 starts", "a1", JobState.New, JobState.Active),
                StateChangeEvent(850, "Async A2 starts", "a2", JobState.New, JobState.Active),
                StateChangeEvent(950, "Launch B1 starts", "b1", JobState.New, JobState.Active),
                StateChangeEvent(1050, "Launch C1 starts", "c1", JobState.New, JobState.Active),
                NarrativeEvent(1200, "B1 encounters a fault — exception propagates up through branch B"),
                StateChangeEvent(1300, "B1 fails", "b1", JobState.Active, JobState.Cancelling),
                StateChangeEvent(1400, "B1 cancelled", "b1", JobState.Cancelling, JobState.Cancelled),
                ExceptionEvent(1500, "Exception from B1 propagates to launch B", "b1", "branch-b", "IOException"),
                StateChangeEvent(1600, "Launch B enters Cancelling", "branch-b", JobState.Active, JobState.Cancelling),
                StateChangeEvent(1700, "Launch B cancelled", "branch-b", JobState.Cancelling, JobState.Cancelled),
                ExceptionEvent(1800, "Exception reaches supervisorScope", "branch-b", "supervisor", "IOException"),
                NarrativeEvent(1900, "SupervisorJob absorbs the exception — branches A and C continue unaffected"),
                StateChangeEvent(2100, "Async A1 completes work", "a1", JobState.Active, JobState.Completing),
                StateChangeEvent(2200, "Async A1 completed", "a1", JobState.Completing, JobState.Completed),
                StateChangeEvent(2300, "Async A2 completes work", "a2", JobState.Active, JobState.Completing),
                StateChangeEvent(2400, "Async A2 completed", "a2", JobState.Completing, JobState.Completed),
                StateChangeEvent(2500, "coroutineScope completing", "scope-a", JobState.Active, JobState.Completing),
                StateChangeEvent(2600, "coroutineScope completed", "scope-a", JobState.Completing, JobState.Completed),
                StateChangeEvent(2700, "Launch A completing", "branch-a", JobState.Active, JobState.Completing),
                StateChangeEvent(2800, "Launch A completed", "branch-a", JobState.Completing, JobState.Completed),
                StateChangeEvent(2900, "Launch C1 completing", "c1", JobState.Active, JobState.Completing),
                StateChangeEvent(3000, "Launch C1 completed", "c1", JobState.Completing, JobState.Completed),
                StateChangeEvent(3100, "Launch C completing", "branch-c", JobState.Active, JobState.Completing),
                StateChangeEvent(3200, "Launch C completed", "branch-c", JobState.Completing, JobState.Completed),
                StateChangeEvent(3300, "supervisorScope completing", "supervisor", JobState.Active, JobState.Completing),
                StateChangeEvent(3400, "supervisorScope completed", "supervisor", JobState.Completing, JobState.Completed),
                StateChangeEvent(3500, "runBlocking completing", "root", JobState.Active, JobState.Completing),
                StateChangeEvent(3600, "runBlocking completed", "root", JobState.Completing, JobState.Completed),
                NarrativeEvent(3700, "Multiple fault boundaries: coroutineScope groups A's children, supervisorScope isolates B's failure from A and C")
            )

            EventTimeline(
                scenarioName = info.name,
                tree = tree,
                events = events,
                kotlinCode = """
fun main() = runBlocking {
    supervisorScope {
        launch {                                // branch A
            coroutineScope {                    // groups A's children
                val a1 = async { "result1" }
                val a2 = async { "result2" }
                println(a1.await() + a2.await())
            }
        }

        launch {                                // branch B (fails)
            launch {                            // B1
                throw IOException("Network error")
            }
        }

        launch {                                // branch C
            launch {                            // C1
                delay(500)
                println("C1 done")
            }
            println("C completed")
        }
    }
    // supervisorScope isolates B's failure
    // coroutineScope inside A groups its async children
}
                """.trimIndent()
            )
        }

        else -> buildTimeline()
    }
}
