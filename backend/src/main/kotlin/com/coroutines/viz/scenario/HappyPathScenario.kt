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
        val tree = node("root", "runBlocking", BuilderType.RunBlocking,
            node("child-1", "launch #1", BuilderType.Launch,
                node("grandchild-1", "launch #1a", BuilderType.Launch)
            ),
            node("child-2", "async #2", BuilderType.Async),
            node("child-3", "launch #3", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Starting runBlocking — creates a root coroutine. (In production code, prefer `suspend fun main() = coroutineScope { ... }`; runBlocking is mainly for samples and tests.)"),
            starts(100, "Root coroutine becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "async #2 starts", "child-2"),
            starts(500, "launch #3 starts", "child-3"),
            starts(700, "launch #1a starts inside launch #1", "grandchild-1"),
            narrative(1000, "All coroutines are now Active, doing work..."),
            completing(1500, "launch #1a finishes its work", "grandchild-1"),
            completed(1600, "launch #1a completed", "grandchild-1"),
            completing(1800, "async #2 finishes its work", "child-2"),
            completed(1900, "async #2 completed", "child-2"),
            completing(2100, "launch #3 finishes its work", "child-3"),
            completed(2200, "launch #3 completed", "child-3"),
            narrative(2300, "launch #1's only child completed — launch #1 can now complete"),
            completing(2400, "launch #1 finishes (all children done)", "child-1"),
            completed(2500, "launch #1 completed", "child-1"),
            narrative(2600, "All children of root have completed — root can finish"),
            completing(2700, "Root enters Completing", "root"),
            completed(2800, "Root completed — structured concurrency ensures orderly shutdown", "root")
        )

        return timeline(
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
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("root", "runBlocking", BuilderType.RunBlocking,
            node("child-1", "launch #1", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Starting runBlocking — creates a root coroutine"),
            starts(100, "Root coroutine becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            narrative(500, "Both coroutines are Active, doing work..."),
            completing(1000, "launch #1 finishes its work", "child-1"),
            completed(1100, "launch #1 completed", "child-1"),
            narrative(1200, "Child completed — root can now finish"),
            completing(1300, "Root enters Completing", "root"),
            completed(1400, "Root completed — structured concurrency ensures orderly shutdown", "root")
        )

        return timeline(
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
        val tree = node("root", "runBlocking", BuilderType.RunBlocking,
            node("child-1", "launch #1", BuilderType.Launch,
                node("gc-1a", "launch #1a", BuilderType.Launch),
                node("gc-1b", "launch #1b", BuilderType.Launch)
            ),
            node("child-2", "async #2", BuilderType.Async,
                node("gc-2a", "async #2a", BuilderType.Async),
                node("gc-2b", "async #2b", BuilderType.Async)
            ),
            node("child-3", "launch #3", BuilderType.Launch,
                node("gc-3a", "launch #3a", BuilderType.Launch),
                node("gc-3b", "launch #3b", BuilderType.Launch)
            )
        )

        val events = listOf(
            // Root starts
            narrative(0, "Starting runBlocking — creates a root coroutine"),
            starts(100, "Root coroutine becomes Active", "root"),

            // Children start
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "async #2 starts", "child-2"),
            starts(500, "launch #3 starts", "child-3"),

            // Grandchildren start
            starts(700, "launch #1a starts inside launch #1", "gc-1a"),
            starts(800, "launch #1b starts inside launch #1", "gc-1b"),
            starts(900, "async #2a starts inside async #2", "gc-2a"),
            starts(1000, "async #2b starts inside async #2", "gc-2b"),
            starts(1100, "launch #3a starts inside launch #3", "gc-3a"),
            starts(1200, "launch #3b starts inside launch #3", "gc-3b"),

            narrative(1400, "All 9 coroutines are now Active, doing work in parallel..."),

            // Grandchildren complete
            completing(1800, "launch #1a finishes", "gc-1a"),
            completed(1900, "launch #1a completed", "gc-1a"),
            completing(2000, "async #2a finishes", "gc-2a"),
            completed(2100, "async #2a completed", "gc-2a"),
            completing(2200, "launch #3a finishes", "gc-3a"),
            completed(2300, "launch #3a completed", "gc-3a"),
            completing(2400, "launch #1b finishes", "gc-1b"),
            completed(2500, "launch #1b completed", "gc-1b"),
            completing(2600, "async #2b finishes", "gc-2b"),
            completed(2700, "async #2b completed", "gc-2b"),
            completing(2800, "launch #3b finishes", "gc-3b"),
            completed(2900, "launch #3b completed", "gc-3b"),

            // Children complete after their grandchildren
            narrative(3000, "All grandchildren done — each parent can now complete"),
            completing(3100, "launch #1 finishes (all children done)", "child-1"),
            completed(3200, "launch #1 completed", "child-1"),
            completing(3300, "async #2 finishes (all children done)", "child-2"),
            completed(3400, "async #2 completed", "child-2"),
            completing(3500, "launch #3 finishes (all children done)", "child-3"),
            completed(3600, "launch #3 completed", "child-3"),

            // Root completes
            narrative(3700, "All children of root have completed — root can finish"),
            completing(3800, "Root enters Completing", "root"),
            completed(3900, "Root completed — structured concurrency ensures orderly shutdown", "root")
        )

        return timeline(
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
