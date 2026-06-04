package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class LazyStartScenario : Scenario {
    override val info = ScenarioInfo(
        id = "lazy-start",
        name = "Lazy Start",
        description = "CoroutineStart.LAZY keeps a coroutine in the New state until something starts it. Compare eager vs lazy, see three ways to wake a lazy coroutine, and watch what happens when one is forgotten.",
        category = "Basics"
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
            node("eager", "launch (eager)", BuilderType.Launch),
            node("lazy", "launch (LAZY)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two children: one eager (default), one lazy (CoroutineStart.LAZY). Watch their initial states diverge."),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "Eager launch starts immediately — default CoroutineStart.DEFAULT", "eager"),
            narrative(500, "The lazy child was created by launch(start = CoroutineStart.LAZY) — its Job exists, but the body hasn't run. It stays in New."),
            narrative(1000, "Eager child is working. Lazy child is still waiting in New — nothing has started it yet."),
            narrative(1500, "Now we call lazyJob.start() — this is the trigger that transitions the lazy Job from New to Active."),
            starts(1700, "Lazy launch starts after .start() call", "lazy"),
            completing(2000, "Eager finishes its work", "eager"),
            completed(2100, "Eager completed", "eager"),
            completing(2400, "Lazy finishes its work", "lazy"),
            completed(2500, "Lazy completed", "lazy"),
            completing(2700, "Scope completing — both children done", "root"),
            completed(2800, "Scope completed", "root"),
            narrative(2900, "Key insight: lazy coroutines exist as a Job in New state but don't run until .start(), .join(), or (for async) .await() transitions them to Active.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {                                     // eager — auto-starts
        delay(200)
        println("Eager done")
    }

    val lazyJob = launch(start = CoroutineStart.LAZY) {
        delay(200)
        println("Lazy done")
    }

    delay(500)
    println("About to start the lazy one...")
    lazyJob.start()                              // New → Active
}
            """.trimIndent()
        )
    }

    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("by-start", "launch LAZY (.start)", BuilderType.Launch),
            node("by-join", "launch LAZY (.join)", BuilderType.Launch),
            node("by-await", "async LAZY (.await)", BuilderType.Async)
        )

        val events = listOf(
            narrative(0, "Three lazy children, each woken a different way: .start() (fire-and-forget), .join() (wait), .await() (wait + get value)."),
            starts(100, "Scope becomes Active", "root"),
            narrative(300, "All three children were created with CoroutineStart.LAZY — all three Jobs are in New state."),
            narrative(700, "Calling .start() on the first lazy Job — transitions it to Active and returns immediately."),
            starts(900, "by-start: New → Active (.start triggered it)", "by-start"),
            narrative(1100, "Calling .join() on the second lazy Job — auto-starts it AND suspends the caller until it completes."),
            starts(1300, "by-join: New → Active (.join started it)", "by-join"),
            narrative(1500, "Calling .await() on the lazy Deferred — auto-starts it AND suspends the caller until the value is ready."),
            starts(1700, "by-await: New → Active (.await started it)", "by-await"),
            narrative(2000, "All three are now running. .start() returned immediately; .join() and .await() are still suspended waiting."),
            completing(2300, "by-start completing", "by-start"),
            completed(2400, "by-start completed", "by-start"),
            completing(2600, "by-join completing", "by-join"),
            completed(2700, "by-join completed — .join() now returns", "by-join"),
            completing(2900, "by-await completing", "by-await"),
            completed(3000, "by-await completed — .await() returns the value", "by-await"),
            completing(3200, "Scope completing", "root"),
            completed(3300, "Scope completed", "root"),
            narrative(3500, "Summary: .start() fires the coroutine and returns. .join() / .await() also fire it but suspend the caller until done. All three transition New → Active.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    val job1 = launch(start = CoroutineStart.LAZY) {
        delay(200)
        println("by .start() done")
    }

    val job2 = launch(start = CoroutineStart.LAZY) {
        delay(200)
        println("by .join() done")
    }

    val deferred = async(start = CoroutineStart.LAZY) {
        delay(200)
        "by .await() value"
    }

    delay(300)
    job1.start()                  // fire-and-forget
    delay(100)
    job2.join()                   // start AND wait
    delay(100)
    val v = deferred.await()      // start AND wait for value
    println(v)
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("lazy-started", "launch LAZY #1 (.start)", BuilderType.Launch),
            node("lazy-forgotten", "launch LAZY #2 (FORGOTTEN)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "The 'forgotten lazy' trap: two lazy children, but only one ever gets .start() called on it. What happens to the scope?"),
            starts(100, "Scope becomes Active", "root"),
            narrative(300, "Both children are in New (lazy). The scope's body launches them and then tries to finish."),
            narrative(700, "We call .start() on #1 — it transitions to Active."),
            starts(900, "Lazy #1: New → Active (.start)", "lazy-started"),
            completing(1300, "Lazy #1 completing", "lazy-started"),
            completed(1400, "Lazy #1 completed", "lazy-started"),
            narrative(1600, "Scope's body has finished. But #2 is STILL in New — nobody started it. The scope waits for ALL children, so this is a deadlock."),
            narrative(2200, "...the scope is hanging. In real code this is a leaked coroutine that prevents the program from terminating."),
            narrative(2800, "Recovery: explicitly cancel the forgotten Job. Cancelling a Job in New goes directly to Cancelled (no body to interrupt)."),
            cancellation(3000, "Explicit cancel() on the forgotten lazy Job", "root", "lazy-forgotten"),
            transition(3100, "Lazy #2: New → Cancelled (cancel on a New job is immediate)", "lazy-forgotten", JobState.New, JobState.Cancelled),
            narrative(3300, "Now all children are in terminal states — the scope can complete."),
            completing(3500, "Scope completing", "root"),
            completed(3600, "Scope completed", "root"),
            narrative(3800, "Lesson: a lazy Job is part of structured concurrency — its parent waits for it. If you create one, you MUST eventually .start() it, .cancel() it, or use a try/finally to guarantee it. Forgetting silently deadlocks the scope.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    val lazy1 = launch(start = CoroutineStart.LAZY) {
        delay(200)
        println("Lazy #1 done")
    }

    val lazy2 = launch(start = CoroutineStart.LAZY) {
        delay(200)
        println("Lazy #2 done") // never reached
    }

    delay(300)
    lazy1.start()
    lazy1.join()

    // Whoops — lazy2 was never started.
    // Without the line below, this scope would hang forever
    // waiting for lazy2 (which is still a child in New state).
    lazy2.cancel()  // New → Cancelled, immediate
}
            """.trimIndent()
        )
    }
}
