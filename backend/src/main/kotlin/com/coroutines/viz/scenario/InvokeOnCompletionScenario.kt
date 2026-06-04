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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child", "launch", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "A child with invokeOnCompletion callback — completes normally"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch starts", "child"),
            narrative(500, "job.invokeOnCompletion { cause -> ... } registered on child"),
            narrative(800, "Child is doing work..."),
            completing(1000, "Child completing", "child"),
            completed(1100, "Child completed", "child"),
            narrative(1300, "invokeOnCompletion fires with cause = null (normal completion)"),
            completing(1500, "Parent scope completing", "root"),
            completed(1600, "Parent scope completed", "root"),
            narrative(1700, "When a coroutine completes normally, invokeOnCompletion's cause parameter is null")
        )

        return timeline(
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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1 (completes)", BuilderType.Launch),
            node("child-2", "launch #2 (fails)", BuilderType.Launch),
            node("child-3", "launch #3 (cancelled)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Three children with invokeOnCompletion: complete, fail, cancel"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "launch #2 starts", "child-2"),
            starts(500, "launch #3 starts", "child-3"),
            narrative(600, "invokeOnCompletion registered on all three children"),
            // Child 1 completes normally
            completing(900, "#1 completing", "child-1"),
            completed(1000, "#1 completed", "child-1"),
            narrative(1100, "#1 callback: cause = null (normal completion)"),
            // Child 2 fails
            narrative(1300, "#2 encounters an exception..."),
            cancelling(1400, "#2 fails — enters Cancelling", "child-2"),
            cancelled(1500, "#2 is Cancelled", "child-2"),
            narrative(1600, "#2 callback: cause = RuntimeException (failure)"),
            // Exception propagates, cancels child 3
            exception(1700, "Exception propagates to parent", "child-2", "root", "RuntimeException: task failed"),
            cancelling(1800, "Parent enters Cancelling", "root"),
            cancellation(1900, "Parent cancels #3", "root", "child-3"),
            cancelling(2000, "#3 enters Cancelling", "child-3"),
            cancelled(2100, "#3 cancelled", "child-3"),
            narrative(2200, "#3 callback: cause = CancellationException (cancelled by parent)"),
            cancelled(2400, "Parent scope cancelled", "root"),
            narrative(2500, "Three outcomes: null (success), RuntimeException (failure), CancellationException (cancelled)")
        )

        return timeline(
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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1", BuilderType.Launch),
            node("child-2", "launch #2", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "invokeOnCompletion on PARENT Job — fires only after ALL children complete"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "launch #2 starts", "child-2"),
            narrative(500, "invokeOnCompletion registered on the PARENT job"),
            narrative(800, "Both children are working..."),
            completing(1000, "#1 completing", "child-1"),
            completed(1100, "#1 completed", "child-1"),
            narrative(1200, "Parent callback has NOT fired yet — child #2 is still running"),
            narrative(1500, "#2 still working..."),
            completing(1800, "#2 completing", "child-2"),
            completed(1900, "#2 completed", "child-2"),
            narrative(2000, "All children completed — parent enters Completing"),
            completing(2100, "Parent scope completing", "root"),
            completed(2200, "Parent scope completed", "root"),
            narrative(2300, "NOW parent's invokeOnCompletion fires with cause = null"),
            narrative(2500, "Parent callback waits for ALL children — useful for tracking overall job completion")
        )

        return timeline(
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
