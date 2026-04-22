package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ChildExceptionScenario : Scenario {
    override val info = ScenarioInfo(
        id = "child-exception",
        name = "Child Exception",
        description = "A child throws an exception — it propagates UP to the parent, which cancels siblings.",
        category = "Exceptions"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
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
            NarrativeEvent(0, "coroutineScope starts with 3 children"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "async #3 starts", "child-3", JobState.New, JobState.Active),
            NarrativeEvent(800, "launch #2 encounters an error and throws an exception!"),
            StateChangeEvent(1000, "launch #2 fails — enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1100, "launch #2 is Cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1300, "Exception propagates UP from launch #2 to parent scope", "child-2", "root", "RuntimeException: Something went wrong"),
            NarrativeEvent(1400, "Parent receives exception — must cancel all other children"),
            StateChangeEvent(1500, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            CancellationEvent(1600, "Parent cancels launch #1", "root", "child-1"),
            StateChangeEvent(1700, "launch #1 enters Cancelling", "child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(1800, "Parent cancels async #3", "root", "child-3"),
            StateChangeEvent(1900, "async #3 enters Cancelling", "child-3", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2100, "launch #1 cancelled", "child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2200, "async #3 cancelled", "child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2400, "Parent scope cancelled — exception re-thrown", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2500, "Exceptions propagate UPWARD, then cancellation flows DOWNWARD to siblings")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {  // launch #1
        delay(2000)
        println("Child 1 done")
    }

    launch {  // launch #2 (fails)
        delay(500)
        throw RuntimeException("Something went wrong")
    }

    async {   // async #3
        delay(2000)
        "result"
    }
    // Exception from #2 cancels #1 and #3, then re-throws
}
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()
        "beginner" -> buildBeginnerTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> buildTimeline()
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child",
                    displayName = "launch (fails)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "coroutineScope starts with 1 child"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch starts", "child", JobState.New, JobState.Active),
            NarrativeEvent(600, "The child coroutine encounters an error and throws an exception!"),
            StateChangeEvent(800, "launch fails — enters Cancelling", "child", JobState.Active, JobState.Cancelling),
            StateChangeEvent(900, "launch is Cancelled", "child", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1100, "Exception propagates UP from child to parent scope", "child", "root", "RuntimeException: Something went wrong"),
            StateChangeEvent(1300, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1500, "Parent scope cancelled — exception re-thrown", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1600, "The exception propagated from child to parent, cancelling the entire scope")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {
        delay(500)
        throw RuntimeException("Something went wrong")
    }
    // Exception propagates up and cancels the scope
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
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
                    displayName = "launch #2",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "grandchild",
                            displayName = "launch (fails)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "async #3",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-4",
                    displayName = "launch #4",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "coroutineScope starts with 4 children; child-2 has a grandchild"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "async #3 starts", "child-3", JobState.New, JobState.Active),
            StateChangeEvent(600, "launch #4 starts", "child-4", JobState.New, JobState.Active),
            StateChangeEvent(700, "grandchild starts inside launch #2", "grandchild", JobState.New, JobState.Active),
            NarrativeEvent(1000, "The grandchild coroutine encounters an error and throws!"),
            StateChangeEvent(1200, "grandchild fails — enters Cancelling", "grandchild", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1300, "grandchild is Cancelled", "grandchild", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1500, "Exception propagates UP from grandchild to launch #2", "grandchild", "child-2", "RuntimeException: Deep failure"),
            StateChangeEvent(1700, "launch #2 enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1800, "launch #2 is Cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(2000, "Exception propagates UP from launch #2 to parent scope", "child-2", "root", "RuntimeException: Deep failure"),
            NarrativeEvent(2100, "Parent receives exception — must cancel all remaining children"),
            StateChangeEvent(2200, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            CancellationEvent(2300, "Parent cancels launch #1", "root", "child-1"),
            StateChangeEvent(2400, "launch #1 enters Cancelling", "child-1", JobState.Active, JobState.Cancelling),
            CancellationEvent(2500, "Parent cancels async #3", "root", "child-3"),
            StateChangeEvent(2600, "async #3 enters Cancelling", "child-3", JobState.Active, JobState.Cancelling),
            CancellationEvent(2700, "Parent cancels launch #4", "root", "child-4"),
            StateChangeEvent(2800, "launch #4 enters Cancelling", "child-4", JobState.Active, JobState.Cancelling),
            StateChangeEvent(3000, "launch #1 cancelled", "child-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3100, "async #3 cancelled", "child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3200, "launch #4 cancelled", "child-4", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(3400, "Parent scope cancelled — exception re-thrown", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(3500, "Exception propagated from grandchild → child-2 → root, then cancellation flowed down to all siblings")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {  // launch #1
        delay(5000)
        println("Child 1 done")
    }

    launch {  // launch #2
        launch {  // grandchild (fails)
            delay(800)
            throw RuntimeException("Deep failure")
        }
        delay(5000)
        println("Child 2 done")
    }

    async {   // async #3
        delay(5000)
        "result"
    }

    launch {  // launch #4
        delay(5000)
        println("Child 4 done")
    }
    // Grandchild exception propagates to #2, then to root,
    // which cancels #1, #3, and #4 before re-throwing
}
            """.trimIndent()
        )
    }
}
