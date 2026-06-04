package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class NonCancellableContextScenario : Scenario {
    override val info = ScenarioInfo(
        id = "non-cancellable",
        name = "NonCancellable Context",
        description = "withContext(NonCancellable) allows cleanup work to run during cancellation.",
        category = "Cancellation"
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
            node("child", "launch (cleanup)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "coroutineScope starts with a child that needs cleanup"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch starts", "child"),
            narrative(600, "Parent scope is cancelled externally..."),
            cancelling(800, "Parent enters Cancelling", "root"),
            cancellation(900, "Parent cancels child", "root", "child"),
            cancelling(1000, "Child enters Cancelling", "child"),
            narrative(1200, "Child uses withContext(NonCancellable) for cleanup — suspend functions work!"),
            narrative(1500, "Cleanup: closing database connection... saving state... flushing cache..."),
            narrative(1800, "Cleanup complete — child can now finish cancellation"),
            cancelled(2000, "Child cancelled after cleanup", "child"),
            cancelled(2200, "Parent scope cancelled", "root"),
            narrative(2300, "NonCancellable allows suspend calls during cancellation — essential for cleanup")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    launch {
        try {
            delay(5000)  // Working...
        } finally {
            // Without NonCancellable, suspend calls would throw
            withContext(NonCancellable) {
                delay(500)  // Cleanup: close DB, save state
                println("Cleanup complete")
            }
        }
    }
    delay(500)
    coroutineContext.cancel()  // Cancel the scope
}
            """.trimIndent()
        )
    }

    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1 (normal)", BuilderType.Launch),
            node("child-2", "launch #2 (NonCancellable)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two children: normal vs NonCancellable cleanup"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 (normal) starts", "child-1"),
            starts(400, "launch #2 (NonCancellable) starts", "child-2"),
            narrative(700, "Parent scope is cancelled..."),
            cancelling(900, "Parent enters Cancelling", "root"),
            cancellation(1000, "Parent cancels child #1", "root", "child-1"),
            cancellation(1050, "Parent cancels child #2", "root", "child-2"),
            cancelling(1100, "Child #1 enters Cancelling", "child-1"),
            cancelling(1150, "Child #2 enters Cancelling", "child-2"),
            narrative(1300, "Child #1 cancels immediately — no NonCancellable cleanup"),
            cancelled(1400, "Child #1 cancelled immediately", "child-1"),
            narrative(1500, "Child #2 runs cleanup via withContext(NonCancellable)..."),
            narrative(1800, "Child #2: saving state to disk... flushing buffers..."),
            narrative(2100, "Child #2: cleanup complete"),
            cancelled(2200, "Child #2 cancelled after cleanup", "child-2"),
            cancelled(2400, "Parent scope cancelled", "root"),
            narrative(2500, "Child #1 stopped instantly — child #2 finished cleanup first. NonCancellable gives time for orderly shutdown.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    launch {  // #1: normal — cancels immediately
        try {
            delay(5000)
        } finally {
            // No NonCancellable — delay() here would throw
            println("Child #1 done (instant)")
        }
    }

    launch {  // #2: NonCancellable cleanup
        try {
            delay(5000)
        } finally {
            withContext(NonCancellable) {
                delay(500)  // Can suspend during cleanup!
                println("Child #2 cleanup complete")
            }
        }
    }

    delay(500)
    coroutineContext.cancel()
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child", "launch (cleanup)", BuilderType.Launch,
                node("cleanup-child", "launch (inside NonCancellable)", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "Nested NonCancellable — launching new coroutines during cleanup"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch starts", "child"),
            narrative(600, "Parent scope is cancelled..."),
            cancelling(800, "Parent enters Cancelling", "root"),
            cancellation(900, "Parent cancels child", "root", "child"),
            cancelling(1000, "Child enters Cancelling", "child"),
            narrative(1200, "Child enters withContext(NonCancellable) for cleanup"),
            narrative(1400, "Inside NonCancellable: launching a NEW coroutine for async cleanup!"),
            starts(1600, "Cleanup coroutine starts inside NonCancellable", "cleanup-child"),
            narrative(1800, "Cleanup coroutine can run suspend functions — withContext(NonCancellable) replaces the Job element in the context, so the new launch is a child of NonCancellable (which is always Active) rather than the outer child's cancelled Job"),
            completing(2000, "Cleanup coroutine completing", "cleanup-child"),
            completed(2100, "Cleanup coroutine completed", "cleanup-child"),
            narrative(2200, "Cleanup done — new coroutines launched inside NonCancellable succeed!"),
            cancelled(2400, "Child cancelled after cleanup", "child"),
            cancelled(2600, "Parent scope cancelled", "root"),
            narrative(2700, "Key insight: NonCancellable creates a fresh scope — you can launch new coroutines for cleanup tasks")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    launch {
        try {
            delay(5000)  // Working...
        } finally {
            withContext(NonCancellable) {
                // Can launch NEW coroutines inside NonCancellable!
                launch {
                    delay(200)
                    println("Async cleanup task complete")
                }
                delay(300)
                println("Main cleanup done")
            }
            // Without NonCancellable, this launch would fail:
            // launch { } // CancellationException!
        }
    }
    delay(500)
    coroutineContext.cancel()
}
            """.trimIndent()
        )
    }
}
