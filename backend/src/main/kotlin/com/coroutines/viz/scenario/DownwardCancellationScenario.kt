package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class DownwardCancellationScenario : Scenario {
    override val info = ScenarioInfo(
        id = "downward-cancellation",
        name = "Downward Cancellation",
        description = "Parent is cancelled — cancellation signal propagates down to all descendants with staggered timing.",
        category = "Cancellation"
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
                    id = "parent",
                    displayName = "launch parent",
                    builder = BuilderType.Launch,
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
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "All coroutines start and become Active"),
            StateChangeEvent(100, "Root becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(200, "Parent launch starts", "parent", JobState.New, JobState.Active),
            StateChangeEvent(300, "Child #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "Async #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "Grandchild #1a starts", "grandchild-1", JobState.New, JobState.Active),
            NarrativeEvent(800, "Now we cancel the parent — watch cancellation propagate DOWNWARD"),
            StateChangeEvent(1000, "Parent enters Cancelling state", "parent", JobState.Active, JobState.Cancelling),
            CancellationEvent(1200, "Cancellation signal sent to child #1", "parent", "child-1"),
            StateChangeEvent(1300, "Child #1 enters Cancelling", "child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(1400, "Cancellation signal sent to async #2", "parent", "child-2"),
            StateChangeEvent(1500, "Async #2 enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            CancellationEvent(1600, "Cancellation cascades deeper to grandchild", "child-1", "grandchild-1"),
            StateChangeEvent(1700, "Grandchild #1a enters Cancelling", "grandchild-1", JobState.Active, JobState.Cancelling),
            NarrativeEvent(1900, "All descendants are now Cancelling — they finish cancellation"),
            StateChangeEvent(2100, "Grandchild #1a cancelled", "grandchild-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2300, "Async #2 cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2500, "Child #1 cancelled (all its children done)", "child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2700, "Parent cancelled", "parent", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2900, "Root completes (child was cancelled)", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(3000, "Root completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3100, "Cancellation always flows DOWNWARD — children cannot cancel parents")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    val parentJob = launch {
        launch {            // child #1
            launch {        // grandchild #1a
                delay(5000)
                println("Grandchild done")
            }
            delay(5000)
            println("Child 1 done")
        }

        async {             // async #2
            delay(5000)
            "result"
        }
    }

    delay(500)
    parentJob.cancel()  // cancels parent + all descendants
    println("Parent cancelled")
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
                    id = "parent",
                    displayName = "launch parent",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "child",
                            displayName = "launch child",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "All coroutines start and become Active"),
            StateChangeEvent(100, "Root becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "Parent starts", "parent", JobState.New, JobState.Active),
            StateChangeEvent(500, "Child starts", "child", JobState.New, JobState.Active),
            NarrativeEvent(800, "Now we cancel the parent — cancellation propagates down to the child"),
            StateChangeEvent(1000, "Parent enters Cancelling state", "parent", JobState.Active, JobState.Cancelling),
            CancellationEvent(1200, "Cancellation signal sent to child", "parent", "child"),
            StateChangeEvent(1400, "Child enters Cancelling", "child", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1700, "Child cancelled", "child", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1900, "Parent cancelled (child is done)", "parent", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2100, "Root completes", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2200, "Root completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2400, "Cancellation flows DOWNWARD — the child was cancelled because its parent was cancelled")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    val parentJob = launch {
        launch {  // child
            delay(5000)
            println("Child done")
        }
    }

    delay(500)
    parentJob.cancel()  // cancels parent and child
    println("Parent cancelled")
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
                    id = "parent",
                    displayName = "launch parent",
                    builder = BuilderType.Launch,
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
                                    id = "gc-1",
                                    displayName = "launch #1a",
                                    builder = BuilderType.Launch,
                                    jobType = JobType.Job,
                                    initialState = JobState.New
                                ),
                                CoroutineNode(
                                    id = "gc-2",
                                    displayName = "async #1b",
                                    builder = BuilderType.Async,
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
            )
        )

        val events = listOf(
            NarrativeEvent(0, "All coroutines start — notice child-2 will complete before cancellation arrives"),
            StateChangeEvent(100, "Root becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(200, "Parent starts", "parent", JobState.New, JobState.Active),
            StateChangeEvent(300, "Child #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "Async #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "Launch #3 starts", "child-3", JobState.New, JobState.Active),
            StateChangeEvent(600, "Grandchild #1a starts", "gc-1", JobState.New, JobState.Active),
            StateChangeEvent(700, "Async grandchild #1b starts", "gc-2", JobState.New, JobState.Active),
            NarrativeEvent(900, "Async #2 finishes its work quickly before any cancellation"),
            StateChangeEvent(1000, "Async #2 completing", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1100, "Async #2 completed", "child-2", JobState.Completing, JobState.Completed),
            NarrativeEvent(1300, "Now we cancel the parent — watch how cancellation skips the already-completed child-2"),
            StateChangeEvent(1500, "Parent enters Cancelling state", "parent", JobState.Active, JobState.Cancelling),
            CancellationEvent(1700, "Cancellation signal sent to child #1", "parent", "child-1"),
            StateChangeEvent(1800, "Child #1 enters Cancelling", "child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(1900, "Cancellation signal sent to child #3", "parent", "child-3"),
            StateChangeEvent(2000, "Launch #3 enters Cancelling", "child-3", JobState.Active, JobState.Cancelling),
            NarrativeEvent(2100, "Child-2 is already Completed — cancellation has no effect on it"),
            CancellationEvent(2200, "Cancellation cascades to grandchild #1a", "child-1", "gc-1"),
            StateChangeEvent(2300, "Grandchild #1a enters Cancelling", "gc-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(2400, "Cancellation cascades to async grandchild #1b", "child-1", "gc-2"),
            StateChangeEvent(2500, "Async grandchild #1b enters Cancelling", "gc-2", JobState.Active, JobState.Cancelling),
            NarrativeEvent(2700, "All active descendants are now Cancelling — they finish cancellation bottom-up"),
            StateChangeEvent(2900, "Grandchild #1a cancelled", "gc-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3100, "Async grandchild #1b cancelled", "gc-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3300, "Launch #3 cancelled", "child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3500, "Child #1 cancelled (all its children done)", "child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3700, "Parent cancelled", "parent", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3900, "Root completes", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(4000, "Root completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(4200, "Cancellation flows downward but only affects active coroutines — child-2 stayed Completed")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    val parentJob = launch {
        launch {                    // child #1
            launch {                // grandchild #1a
                delay(5000)
                println("Grandchild 1a done")
            }
            async {                 // grandchild #1b
                delay(5000)
                "grandchild result"
            }
            delay(5000)
            println("Child 1 done")
        }

        async {                     // child #2 — completes fast
            delay(100)
            "fast result"
        }

        launch {                    // child #3
            delay(5000)
            println("Child 3 done")
        }
    }

    delay(500)
    parentJob.cancel()  // cancels parent + active descendants
    // child-2 already completed — unaffected
    println("Parent cancelled")
}
            """.trimIndent()
        )
    }
}
