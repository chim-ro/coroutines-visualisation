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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("parent", "launch parent", BuilderType.Launch,
                node("child-1", "launch #1", BuilderType.Launch,
                    node("grandchild-1", "launch #1a", BuilderType.Launch)
                ),
                node("child-2", "async #2", BuilderType.Async)
            )
        )

        val events = listOf(
            narrative(0, "All coroutines start and become Active"),
            starts(100, "Root becomes Active", "root"),
            starts(200, "Parent launch starts", "parent"),
            starts(300, "Child #1 starts", "child-1"),
            starts(400, "Async #2 starts", "child-2"),
            starts(500, "Grandchild #1a starts", "grandchild-1"),
            narrative(800, "Now we cancel the parent — watch cancellation propagate DOWNWARD"),
            cancelling(1000, "Parent enters Cancelling state", "parent"),
            cancellation(1200, "Cancellation signal sent to child #1", "parent", "child-1"),
            cancelling(1300, "Child #1 enters Cancelling", "child-1"),
            cancellation(1400, "Cancellation signal sent to async #2", "parent", "child-2"),
            cancelling(1500, "Async #2 enters Cancelling", "child-2"),
            cancellation(1600, "Cancellation cascades deeper to grandchild", "child-1", "grandchild-1"),
            cancelling(1700, "Grandchild #1a enters Cancelling", "grandchild-1"),
            narrative(1900, "All descendants are now Cancelling — they finish cancellation"),
            cancelled(2100, "Grandchild #1a cancelled", "grandchild-1"),
            cancelled(2300, "Async #2 cancelled", "child-2"),
            cancelled(2500, "Child #1 cancelled (all its children done)", "child-1"),
            cancelled(2700, "Parent cancelled", "parent"),
            completing(2900, "Root completes (child was cancelled)", "root"),
            completed(3000, "Root completed", "root"),
            narrative(3100, "Cancellation always flows DOWNWARD — children cannot cancel parents")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
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
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("parent", "launch parent", BuilderType.Launch,
                node("child", "launch child", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "All coroutines start and become Active"),
            starts(100, "Root becomes Active", "root"),
            starts(300, "Parent starts", "parent"),
            starts(500, "Child starts", "child"),
            narrative(800, "Now we cancel the parent — cancellation propagates down to the child"),
            cancelling(1000, "Parent enters Cancelling state", "parent"),
            cancellation(1200, "Cancellation signal sent to child", "parent", "child"),
            cancelling(1400, "Child enters Cancelling", "child"),
            cancelled(1700, "Child cancelled", "child"),
            cancelled(1900, "Parent cancelled (child is done)", "parent"),
            completing(2100, "Root completes", "root"),
            completed(2200, "Root completed", "root"),
            narrative(2400, "Cancellation flows DOWNWARD — the child was cancelled because its parent was cancelled")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("parent", "launch parent", BuilderType.Launch,
                node("child-1", "launch #1", BuilderType.Launch,
                    node("gc-1", "launch #1a", BuilderType.Launch),
                    node("gc-2", "async #1b", BuilderType.Async)
                ),
                node("child-2", "async #2", BuilderType.Async),
                node("child-3", "launch #3", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "All coroutines start — notice child-2 will complete before cancellation arrives"),
            starts(100, "Root becomes Active", "root"),
            starts(200, "Parent starts", "parent"),
            starts(300, "Child #1 starts", "child-1"),
            starts(400, "Async #2 starts", "child-2"),
            starts(500, "Launch #3 starts", "child-3"),
            starts(600, "Grandchild #1a starts", "gc-1"),
            starts(700, "Async grandchild #1b starts", "gc-2"),
            narrative(900, "Async #2 finishes its work quickly before any cancellation"),
            completing(1000, "Async #2 completing", "child-2"),
            completed(1100, "Async #2 completed", "child-2"),
            narrative(1300, "Now we cancel the parent — watch how cancellation skips the already-completed child-2"),
            cancelling(1500, "Parent enters Cancelling state", "parent"),
            cancellation(1700, "Cancellation signal sent to child #1", "parent", "child-1"),
            cancelling(1800, "Child #1 enters Cancelling", "child-1"),
            cancellation(1900, "Cancellation signal sent to child #3", "parent", "child-3"),
            cancelling(2000, "Launch #3 enters Cancelling", "child-3"),
            narrative(2100, "Child-2 is already Completed — cancellation has no effect on it"),
            cancellation(2200, "Cancellation cascades to grandchild #1a", "child-1", "gc-1"),
            cancelling(2300, "Grandchild #1a enters Cancelling", "gc-1"),
            cancellation(2400, "Cancellation cascades to async grandchild #1b", "child-1", "gc-2"),
            cancelling(2500, "Async grandchild #1b enters Cancelling", "gc-2"),
            narrative(2700, "All active descendants are now Cancelling — they finish cancellation bottom-up"),
            cancelled(2900, "Grandchild #1a cancelled", "gc-1"),
            cancelled(3100, "Async grandchild #1b cancelled", "gc-2"),
            cancelled(3300, "Launch #3 cancelled", "child-3"),
            cancelled(3500, "Child #1 cancelled (all its children done)", "child-1"),
            cancelled(3700, "Parent cancelled", "parent"),
            completing(3900, "Root completes", "root"),
            completed(4000, "Root completed", "root"),
            narrative(4200, "Cancellation flows downward but only affects active coroutines — child-2 stayed Completed")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
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
