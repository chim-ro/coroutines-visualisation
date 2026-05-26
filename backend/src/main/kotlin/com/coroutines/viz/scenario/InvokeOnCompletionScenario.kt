package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class InvokeOnCompletionScenario : Scenario {
    override val info = ScenarioInfo(
        id = "invoke-on-completion",
        name = "invokeOnCompletion",
        description = "invokeOnCompletion callbacks fire on completion, failure, or cancellation.",
        category = "Lifecycle"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
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
                    displayName = "launch",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "A child with invokeOnCompletion callback — completes normally"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch starts", "child", JobState.New, JobState.Active),
            NarrativeEvent(500, "job.invokeOnCompletion { cause -> ... } registered on child"),
            NarrativeEvent(800, "Child is doing work..."),
            StateChangeEvent(1000, "Child completing", "child", JobState.Active, JobState.Completing),
            StateChangeEvent(1100, "Child completed", "child", JobState.Completing, JobState.Completed),
            NarrativeEvent(1300, "invokeOnCompletion fires with cause = null (normal completion)"),
            StateChangeEvent(1500, "Parent scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(1600, "Parent scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(1700, "When a coroutine completes normally, invokeOnCompletion's cause parameter is null")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    val job = launch {
        delay(500)
        println("Work done")
    }

    job.invokeOnCompletion { cause ->
        when (cause) {
            null -> println("Completed normally!")
            else -> println("Failed: ${'$'}cause")
        }
    }
}
            """.trimIndent()
        )
    }

    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1 (completes)",
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
                    displayName = "launch #3 (cancelled)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Three children with invokeOnCompletion: complete, fail, cancel"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #3 starts", "child-3", JobState.New, JobState.Active),
            NarrativeEvent(600, "invokeOnCompletion registered on all three children"),
            // Child 1 completes normally
            StateChangeEvent(900, "#1 completing", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1000, "#1 completed", "child-1", JobState.Completing, JobState.Completed),
            NarrativeEvent(1100, "#1 callback: cause = null (normal completion)"),
            // Child 2 fails
            NarrativeEvent(1300, "#2 encounters an exception..."),
            StateChangeEvent(1400, "#2 fails — enters Cancelling", "child-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1500, "#2 is Cancelled", "child-2", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1600, "#2 callback: cause = RuntimeException (failure)"),
            // Exception propagates, cancels child 3
            ExceptionEvent(1700, "Exception propagates to parent", "child-2", "root", "RuntimeException: task failed"),
            StateChangeEvent(1800, "Parent enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            CancellationEvent(1900, "Parent cancels #3", "root", "child-3"),
            StateChangeEvent(2000, "#3 enters Cancelling", "child-3", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2100, "#3 cancelled", "child-3", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2200, "#3 callback: cause = CancellationException (cancelled by parent)"),
            StateChangeEvent(2400, "Parent scope cancelled", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2500, "Three outcomes: null (success), RuntimeException (failure), CancellationException (cancelled)")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    val job1 = launch {  // completes normally
        delay(300)
        println("Done")
    }

    val job2 = launch {  // fails
        delay(600)
        throw RuntimeException("task failed")
    }

    val job3 = launch {  // will be cancelled
        delay(5000)
    }

    listOf(job1, job2, job3).forEach { job ->
        job.invokeOnCompletion { cause ->
            when (cause) {
                null -> println("Completed!")
                is CancellationException -> println("Cancelled: ${'$'}cause")
                else -> println("Failed: ${'$'}cause")
            }
        }
    }
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
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "invokeOnCompletion on PARENT Job — fires only after ALL children complete"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            NarrativeEvent(500, "invokeOnCompletion registered on the PARENT job"),
            NarrativeEvent(800, "Both children are working..."),
            StateChangeEvent(1000, "#1 completing", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1100, "#1 completed", "child-1", JobState.Completing, JobState.Completed),
            NarrativeEvent(1200, "Parent callback has NOT fired yet — child #2 is still running"),
            NarrativeEvent(1500, "#2 still working..."),
            StateChangeEvent(1800, "#2 completing", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1900, "#2 completed", "child-2", JobState.Completing, JobState.Completed),
            NarrativeEvent(2000, "All children completed — parent enters Completing"),
            StateChangeEvent(2100, "Parent scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2200, "Parent scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2300, "NOW parent's invokeOnCompletion fires with cause = null"),
            NarrativeEvent(2500, "Parent callback waits for ALL children — useful for tracking overall job completion")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    val parentJob = coroutineContext[Job]!!

    parentJob.invokeOnCompletion { cause ->
        // Fires ONLY when ALL children are done
        println("All work complete! cause=${'$'}cause")
    }

    launch {  // #1
        delay(500)
        println("Child 1 done")
    }

    launch {  // #2
        delay(1000)
        println("Child 2 done")
    }
    // Parent callback fires after both #1 and #2 complete
}
            """.trimIndent()
        )
    }
}
