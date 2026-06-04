package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class CooperativeCancellationScenario : Scenario {
    override val info = ScenarioInfo(
        id = "cooperative-cancellation",
        name = "Cooperative Cancellation",
        description = "Cooperative (isActive/ensureActive) vs non-cooperative cancellation behavior.",
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
            node("child", "launch (cooperative)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "A cooperative coroutine checks for cancellation"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch starts — doing CPU-bound work", "child"),
            narrative(600, "Child uses delay() / ensureActive() — these are cancellation points"),
            narrative(900, "Parent cancels the child..."),
            cancellation(1000, "Parent sends cancellation signal", "root", "child"),
            narrative(1200, "Child reaches next cancellation point (delay/ensureActive)..."),
            cancelling(1400, "Child detects cancellation — enters Cancelling", "child"),
            cancelled(1600, "Child is Cancelled", "child"),
            completing(1800, "Parent scope completing", "root"),
            completed(1900, "Parent scope completed", "root"),
            narrative(2000, "Cooperative cancellation: coroutine checks for cancellation at suspension points")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    val job = launch {
        repeat(1000) { i ->
            ensureActive()  // Cancellation check point!
            // Heavy computation...
            println("Processing item ${'$'}i")
            delay(100)      // Also a cancellation point
        }
    }
    delay(500)
    job.cancel()  // Sends cancellation signal
    job.join()    // Waits for coroutine to finish
}
            """.trimIndent()
        )
    }

    private fun buildIntermediateTimeline(): EventTimeline {
        // Dual-tree: cooperative vs non-cooperative
        val tree1 = node("coop-root", "coroutineScope", BuilderType.CoroutineScope,
            node("coop-child", "launch (cooperative)", BuilderType.Launch)
        )

        val tree2 = node("noncoop-root", "coroutineScope", BuilderType.CoroutineScope,
            node("noncoop-child", "launch (non-cooperative)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Left: cooperative child (checks isActive) | Right: non-cooperative (ignores cancellation)"),
            // Both scopes start
            starts(100, "Left scope starts", "coop-root"),
            starts(150, "Right scope starts", "noncoop-root"),
            starts(300, "Cooperative child starts", "coop-child"),
            starts(350, "Non-cooperative child starts", "noncoop-child"),
            narrative(700, "Both parents send cancellation signal..."),
            cancellation(800, "Left: cancellation signal sent", "coop-root", "coop-child"),
            cancellation(850, "Right: cancellation signal sent", "noncoop-root", "noncoop-child"),
            cancelling(870, "Right child's Job flips to Cancelling immediately (but body keeps running)", "noncoop-child"),
            narrative(1000, "Cooperative child checks isActive — detects cancellation!"),
            cancelling(1200, "Cooperative child enters Cancelling", "coop-child"),
            cancelled(1400, "Cooperative child cancelled", "coop-child"),
            narrative(1500, "Non-cooperative child ignores the signal — body still running while Job is Cancelling"),
            narrative(1800, "Non-cooperative child still doing work..."),
            narrative(2100, "Non-cooperative child still running — no cancellation check!"),
            narrative(2300, "Non-cooperative body finishes — but Job was Cancelling, so final state is Cancelled (not Completed)"),
            cancelled(2400, "Non-cooperative child Cancelled (cancel() wins over natural completion)", "noncoop-child"),
            // Both scopes finish
            completing(2600, "Left scope completing", "coop-root"),
            completing(2650, "Right scope completing", "noncoop-root"),
            completed(2700, "Left scope completed", "coop-root"),
            completed(2750, "Right scope completed", "noncoop-root"),
            narrative(2900, "Both children ended in Cancelled — the difference is whether the body cooperated (left stopped early) or not (right ran to the end but still ends Cancelled)")
        )

        return timeline(
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: Cooperative — checks for cancellation
coroutineScope {
    val job = launch {
        while (isActive) {  // Checks cancellation flag
            // Heavy computation...
        }
    }
    delay(500)
    job.cancelAndJoin()  // Child stops at next isActive check
}

// RIGHT: Non-cooperative — ignores cancellation
coroutineScope {
    val job = launch {
        while (true) {  // No cancellation check!
            Thread.sleep(100)  // Blocking, not suspending
        }
    }
    delay(500)
    job.cancelAndJoin()  // Child ignores cancellation, runs to completion
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1 (ensureActive)", BuilderType.Launch),
            node("child-2", "launch #2 (isActive)", BuilderType.Launch),
            node("child-3", "launch #3 (non-cooperative)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Three strategies: ensureActive() (throws), isActive (graceful), non-cooperative (ignores)"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 (ensureActive) starts", "child-1"),
            starts(400, "launch #2 (isActive) starts", "child-2"),
            starts(500, "launch #3 (non-cooperative) starts", "child-3"),
            narrative(800, "Parent cancels all children..."),
            cancellation(900, "Cancellation signal to #1", "root", "child-1"),
            cancellation(950, "Cancellation signal to #2", "root", "child-2"),
            cancellation(1000, "Cancellation signal to #3", "root", "child-3"),
            cancelling(1020, "#3's Job flips to Cancelling immediately (body unaware, keeps running)", "child-3"),
            narrative(1100, "#1 calls ensureActive() — throws CancellationException immediately!"),
            cancelling(1200, "#1 enters Cancelling (ensureActive threw)", "child-1"),
            cancelled(1300, "#1 cancelled", "child-1"),
            narrative(1400, "#2 checks isActive — false! Exits loop gracefully, runs cleanup"),
            cancelling(1600, "#2 enters Cancelling (graceful exit)", "child-2"),
            narrative(1700, "#2 performs cleanup before finishing cancellation..."),
            cancelled(1900, "#2 cancelled after cleanup", "child-2"),
            narrative(2000, "#3 has no cancellation checks — body keeps running, but Job is Cancelling"),
            narrative(2300, "#3 still ignoring cancellation signal..."),
            narrative(2500, "#3 body finishes — Job was Cancelling, so final state is Cancelled (not Completed)"),
            cancelled(2600, "#3 Cancelled (cancel() wins over natural completion)", "child-3"),
            completing(2800, "Parent scope completing", "root"),
            completed(2900, "Parent scope completed", "root"),
            narrative(3000, "ensureActive(): fastest, throws immediately | isActive: graceful, allows cleanup | non-cooperative: still ends Cancelled, but only after body runs to completion"),
            narrative(3100, "A fourth option: yield() — like ensureActive() it throws on cancellation, AND it gives other coroutines a chance to run by rotating the dispatcher queue. Prefer yield() in long CPU-bound loops where you also want cooperative scheduling.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
coroutineScope {
    val job1 = launch {  // ensureActive() — throws immediately
        repeat(1000) { i ->
            ensureActive()  // Throws CancellationException
            heavyComputation(i)
        }
    }

    val job2 = launch {  // isActive — graceful exit
        var i = 0
        while (isActive) {  // Returns false when cancelled
            heavyComputation(i++)
        }
        println("Graceful shutdown, processed ${'$'}i items")
    }

    val job3 = launch {  // Non-cooperative — no checks
        repeat(1000) { i ->
            Thread.sleep(10)  // Blocking! No cancellation check
        }
    }

    // Fourth option (not shown above):
    //   yield() — suspends briefly, throws if cancelled,
    //   AND rotates the dispatcher queue so other coroutines run.
    //   Best for CPU-bound loops on a shared dispatcher.

    delay(500)
    coroutineContext.cancelChildren()
}
            """.trimIndent()
        )
    }
}
