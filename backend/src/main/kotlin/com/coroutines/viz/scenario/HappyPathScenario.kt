package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class HappyPathScenario : Scenario {
    override val info = ScenarioInfo(
        id = "happy-path",
        name = "Happy Path",
        description = "All coroutines complete normally. Parent waits for children before completing.",
        category = "Basics"
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
                    id = "child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "grandchild-1",
                            displayName = "launch #1a",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "async #2",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "launch #3",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Starting runBlocking — creates a root coroutine"),
            StateChangeEvent(100, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "async #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #3 starts", "child-3", JobState.New, JobState.Active),
            StateChangeEvent(700, "launch #1a starts inside launch #1", "grandchild-1", JobState.New, JobState.Active),
            NarrativeEvent(1000, "All coroutines are now Active, doing work..."),
            StateChangeEvent(1500, "launch #1a finishes its work", "grandchild-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1600, "launch #1a completed", "grandchild-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(1800, "async #2 finishes its work", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1900, "async #2 completed", "child-2", JobState.Completing, JobState.Completed),
            StateChangeEvent(2100, "launch #3 finishes its work", "child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(2200, "launch #3 completed", "child-3", JobState.Completing, JobState.Completed),
            NarrativeEvent(2300, "launch #1's only child completed — launch #1 can now complete"),
            StateChangeEvent(2400, "launch #1 finishes (all children done)", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(2500, "launch #1 completed", "child-1", JobState.Completing, JobState.Completed),
            NarrativeEvent(2600, "All children of root have completed — root can finish"),
            StateChangeEvent(2700, "Root enters Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2800, "Root completed — structured concurrency ensures orderly shutdown", "root", JobState.Completing, JobState.Completed)
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    launch {                // launch #1
        launch {            // launch #1a
            delay(100)
            println("Grandchild done")
        }
        delay(200)
        println("Child 1 done")
    }

    val result = async {    // async #2
        delay(150)
        "computed value"
    }

    launch {                // launch #3
        delay(180)
        println("Child 3 done")
    }

    println(result.await())
    println("All children completed")
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
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Starting runBlocking — creates a root coroutine"),
            StateChangeEvent(100, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            NarrativeEvent(500, "Both coroutines are Active, doing work..."),
            StateChangeEvent(1000, "launch #1 finishes its work", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1100, "launch #1 completed", "child-1", JobState.Completing, JobState.Completed),
            NarrativeEvent(1200, "Child completed — root can now finish"),
            StateChangeEvent(1300, "Root enters Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(1400, "Root completed — structured concurrency ensures orderly shutdown", "root", JobState.Completing, JobState.Completed)
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    launch {
        delay(100)
        println("Child done")
    }
    println("Root waiting for child...")
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "gc-1a",
                            displayName = "launch #1a",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "gc-1b",
                            displayName = "launch #1b",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "async #2",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "gc-2a",
                            displayName = "async #2a",
                            builder = BuilderType.Async,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "gc-2b",
                            displayName = "async #2b",
                            builder = BuilderType.Async,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "launch #3",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "gc-3a",
                            displayName = "launch #3a",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "gc-3b",
                            displayName = "launch #3b",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            // Root starts
            NarrativeEvent(0, "Starting runBlocking — creates a root coroutine"),
            StateChangeEvent(100, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),

            // Children start
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "async #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #3 starts", "child-3", JobState.New, JobState.Active),

            // Grandchildren start
            StateChangeEvent(700, "launch #1a starts inside launch #1", "gc-1a", JobState.New, JobState.Active),
            StateChangeEvent(800, "launch #1b starts inside launch #1", "gc-1b", JobState.New, JobState.Active),
            StateChangeEvent(900, "async #2a starts inside async #2", "gc-2a", JobState.New, JobState.Active),
            StateChangeEvent(1000, "async #2b starts inside async #2", "gc-2b", JobState.New, JobState.Active),
            StateChangeEvent(1100, "launch #3a starts inside launch #3", "gc-3a", JobState.New, JobState.Active),
            StateChangeEvent(1200, "launch #3b starts inside launch #3", "gc-3b", JobState.New, JobState.Active),

            NarrativeEvent(1400, "All 9 coroutines are now Active, doing work in parallel..."),

            // Grandchildren complete
            StateChangeEvent(1800, "launch #1a finishes", "gc-1a", JobState.Active, JobState.Completing),
            StateChangeEvent(1900, "launch #1a completed", "gc-1a", JobState.Completing, JobState.Completed),
            StateChangeEvent(2000, "async #2a finishes", "gc-2a", JobState.Active, JobState.Completing),
            StateChangeEvent(2100, "async #2a completed", "gc-2a", JobState.Completing, JobState.Completed),
            StateChangeEvent(2200, "launch #3a finishes", "gc-3a", JobState.Active, JobState.Completing),
            StateChangeEvent(2300, "launch #3a completed", "gc-3a", JobState.Completing, JobState.Completed),
            StateChangeEvent(2400, "launch #1b finishes", "gc-1b", JobState.Active, JobState.Completing),
            StateChangeEvent(2500, "launch #1b completed", "gc-1b", JobState.Completing, JobState.Completed),
            StateChangeEvent(2600, "async #2b finishes", "gc-2b", JobState.Active, JobState.Completing),
            StateChangeEvent(2700, "async #2b completed", "gc-2b", JobState.Completing, JobState.Completed),
            StateChangeEvent(2800, "launch #3b finishes", "gc-3b", JobState.Active, JobState.Completing),
            StateChangeEvent(2900, "launch #3b completed", "gc-3b", JobState.Completing, JobState.Completed),

            // Children complete after their grandchildren
            NarrativeEvent(3000, "All grandchildren done — each parent can now complete"),
            StateChangeEvent(3100, "launch #1 finishes (all children done)", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(3200, "launch #1 completed", "child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(3300, "async #2 finishes (all children done)", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(3400, "async #2 completed", "child-2", JobState.Completing, JobState.Completed),
            StateChangeEvent(3500, "launch #3 finishes (all children done)", "child-3", JobState.Active, JobState.Completing),
            StateChangeEvent(3600, "launch #3 completed", "child-3", JobState.Completing, JobState.Completed),

            // Root completes
            NarrativeEvent(3700, "All children of root have completed — root can finish"),
            StateChangeEvent(3800, "Root enters Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(3900, "Root completed — structured concurrency ensures orderly shutdown", "root", JobState.Completing, JobState.Completed)
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    launch {                        // launch #1
        launch {                    // launch #1a
            delay(100)
            println("Grandchild 1a done")
        }
        launch {                    // launch #1b
            delay(150)
            println("Grandchild 1b done")
        }
    }

    val parent = async {            // async #2
        val a = async {             // async #2a
            delay(120)
            10
        }
        val b = async {             // async #2b
            delay(160)
            20
        }
        a.await() + b.await()
    }

    launch {                        // launch #3
        launch {                    // launch #3a
            delay(110)
            println("Grandchild 3a done")
        }
        launch {                    // launch #3b
            delay(170)
            println("Grandchild 3b done")
        }
    }

    println("Result: ${'$'}{parent.await()}")
    println("All children completed")
}
            """.trimIndent()
        )
    }
}
