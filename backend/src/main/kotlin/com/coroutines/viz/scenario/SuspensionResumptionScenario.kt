package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class SuspensionResumptionScenario : Scenario {
    override val info = ScenarioInfo(
        id = "suspension-resumption",
        name = "Suspension & Resumption",
        description = "A coroutine hits delay() and suspends — freeing the thread for other coroutines. When the delay expires, it resumes and completes.",
        category = "Basics"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = node("root", "runBlocking", BuilderType.RunBlocking,
            node("fetcher", "launch (fetcher)", BuilderType.Launch),
            node("processor", "launch (processor)", BuilderType.Launch),
            node("logger", "launch (logger)", BuilderType.Launch)
        )

        val events = listOf(
            // Phase 1: Setup — all three launches create coroutines, all go Active immediately
            narrative(0, "All three coroutines share a single thread. Note: this single-thread behavior is specific to runBlocking's confined dispatcher — on Dispatchers.Default the same coroutines would run in parallel across CPU cores."),
            starts(200, "Root coroutine becomes Active", "root"),
            starts(500, "Fetcher launched — Job state becomes Active", "fetcher"),
            starts(600, "Processor launched — Job state becomes Active", "processor"),
            starts(700, "Logger launched — Job state becomes Active", "logger"),
            narrative(900, "All three are Active, but runBlocking's dispatcher runs only one body at a time on its single thread. Fetcher's body runs first."),

            // Phase 2: Fetcher suspends
            narrative(1200, "Fetcher calls delay(1000) — simulating a network request. The coroutine SUSPENDS."),
            suspends(1400, "Fetcher suspends — the thread is now FREE", "fetcher"),
            narrative(1600, "Key insight: the thread is not blocked! It's released back to the dispatcher."),

            // Phase 3: Other coroutines (already Active) get thread time
            narrative(2000, "With the thread free, the dispatcher runs the next ready coroutine. Processor and Logger were already Active — now they get to execute."),
            narrative(3200, "Processor and Logger are doing real work while Fetcher sleeps."),

            // Phase 4: Other coroutines complete their work
            completing(3800, "Processor finishes its work", "processor"),
            completed(4100, "Processor completed", "processor"),
            completing(4500, "Logger finishes its work", "logger"),
            completed(4800, "Logger completed", "logger"),

            // Phase 5: Fetcher resumes — the aha moment
            narrative(5400, "delay(1000) expires — the dispatcher RESUMES the fetcher coroutine."),
            resumes(5700, "Fetcher RESUMES — picks up exactly where it left off", "fetcher"),
            narrative(6100, "This is the magic: no threads were blocked. Suspension is cooperative, not preemptive."),

            // Phase 6: Everyone completes
            completing(6600, "Fetcher finishes processing the response", "fetcher"),
            completed(6900, "Fetcher completed", "fetcher"),
            narrative(7200, "All children are done — root can complete."),
            completing(7500, "Root enters Completing", "root"),
            completed(7800, "Root completed — one thread served all three coroutines via suspension", "root")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
fun main() = runBlocking {
    launch {  // fetcher
        println("Fetcher: starting network request")
        delay(1000)  // suspends here — thread is FREE
        println("Fetcher: response received, processing")
    }

    launch {  // processor
        println("Processor: crunching data")
        delay(500)
        println("Processor: done")
    }

    launch {  // logger
        println("Logger: writing logs")
        delay(600)
        println("Logger: done")
    }
}
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()

        "beginner" -> {
            val tree = node("root", "runBlocking", BuilderType.RunBlocking,
                node("worker", "launch (worker)", BuilderType.Launch)
            )

            val events = listOf(
                // Phase 1: Both start
                narrative(0, "A single coroutine demonstrates suspension. Watch how delay() pauses without blocking the thread."),
                starts(200, "Root coroutine becomes Active", "root"),
                starts(600, "Worker coroutine starts", "worker"),

                // Phase 2: Worker suspends
                narrative(1200, "Worker calls delay(500) — it SUSPENDS. The underlying thread is not blocked."),
                suspends(1400, "Worker suspends on delay()", "worker"),
                narrative(1800, "The thread is free! In a real app, other coroutines could run now."),

                // Phase 3: Worker resumes
                narrative(2400, "delay(500) expires — the dispatcher resumes the worker."),
                resumes(2600, "Worker RESUMES and continues execution", "worker"),

                // Phase 4: Completion
                completing(3200, "Worker finishes its work", "worker"),
                completed(3500, "Worker completed", "worker"),
                narrative(3800, "Child is done — root can now complete."),
                completing(4100, "Root enters Completing", "root"),
                completed(4400, "Root completed", "root")
            )

            timeline(
                tree = tree,
                events = events,
                kotlinCode = """
fun main() = runBlocking {
    launch {  // worker
        println("Worker: starting")
        delay(500)  // suspends here — thread is FREE
        println("Worker: resumed after delay")
    }
}
                """.trimIndent()
            )
        }

        "advanced" -> {
            val tree = node("root", "runBlocking", BuilderType.RunBlocking,
                node("fetcher", "launch (fetcher)", BuilderType.Launch),
                node("processor", "launch (processor)", BuilderType.Launch),
                node("logger", "launch (logger)", BuilderType.Launch),
                node("cache", "async (cache)", BuilderType.Async)
            )

            val events = listOf(
                // Phase 1: All four launches create coroutines, all go Active immediately
                narrative(0, "Four coroutines share a single thread. Multiple suspend/resume cycles show true cooperative multitasking."),
                starts(200, "Root coroutine becomes Active", "root"),
                starts(400, "Fetcher launched — Job state becomes Active", "fetcher"),
                starts(500, "Processor launched — Job state becomes Active", "processor"),
                starts(600, "Logger launched — Job state becomes Active", "logger"),
                starts(700, "Cache async launched — Job state becomes Active", "cache"),
                narrative(1000, "All four are Active, but only one body runs at a time. The dispatcher starts with Fetcher."),

                // Phase 2: Fetcher suspends (first time)
                narrative(1800, "Fetcher hits its first delay() — first network call. Thread is released."),
                suspends(2000, "Fetcher suspends on first network call", "fetcher"),

                // Phase 3: Processor runs then suspends
                narrative(2400, "With fetcher suspended, processor gets thread time and does its work."),
                suspends(2800, "Processor suspends while waiting for data", "processor"),

                // Phase 4: Logger runs, cache runs (both already Active, just getting thread time)
                narrative(3200, "Thread bounces to logger, then cache — cooperative scheduling in action."),
                narrative(3400, "Logger writes first batch of logs"),
                narrative(3800, "Cache computes and stores result"),

                // Phase 5: Fetcher resumes and suspends again (second network call)
                narrative(4200, "Fetcher's first delay() expires — it resumes for its second network call."),
                resumes(4400, "Fetcher RESUMES from first suspension", "fetcher"),
                narrative(4800, "Fetcher immediately hits another delay() — second network call. Suspends again!"),
                suspends(5000, "Fetcher suspends AGAIN on second network call", "fetcher"),

                // Phase 6: Cache completes
                narrative(5400, "Cache finishes its async computation and delivers the Deferred result."),
                completing(5600, "Cache enters Completing", "cache"),
                completed(5800, "Cache completed — result is available via await()", "cache"),

                // Phase 7: Logger suspends
                narrative(6200, "Logger needs to flush — it suspends to wait for I/O."),
                suspends(6400, "Logger suspends on I/O flush", "logger"),

                // Phase 8: Processor resumes and completes
                narrative(6800, "Processor's data arrived — it resumes and finishes."),
                resumes(7000, "Processor resumes", "processor"),
                completing(7400, "Processor enters Completing", "processor"),
                completed(7600, "Processor completed", "processor"),

                // Phase 9: Fetcher resumes (second time) and completes
                narrative(8000, "Fetcher's second delay() expires — it resumes for the last time."),
                resumes(8200, "Fetcher RESUMES from second suspension", "fetcher"),
                completing(8600, "Fetcher enters Completing", "fetcher"),
                completed(8800, "Fetcher completed — both network calls done", "fetcher"),

                // Phase 10: Logger resumes and completes
                resumes(9200, "Logger resumes after I/O flush", "logger"),
                completing(9600, "Logger enters Completing", "logger"),
                completed(9800, "Logger completed", "logger"),

                // Phase 11: Root completes
                narrative(10200, "All four children are done. One thread handled everything — suspension made it possible."),
                completing(10500, "Root enters Completing", "root"),
                completed(10800, "Root completed — thread reuse via multiple suspend/resume cycles", "root")
            )

            timeline(
                tree = tree,
                events = events,
                kotlinCode = """
fun main() = runBlocking {
    launch {  // fetcher — suspends TWICE
        println("Fetcher: first network call")
        delay(1000)  // 1st suspension
        println("Fetcher: first response, making second call")
        delay(800)   // 2nd suspension
        println("Fetcher: second response received")
    }

    launch {  // processor
        println("Processor: waiting for data")
        delay(600)
        println("Processor: data received, crunching")
    }

    launch {  // logger
        println("Logger: writing logs")
        delay(400)
        println("Logger: flushing to disk")
        delay(700)  // suspends for I/O
        println("Logger: flush complete")
    }

    val cached = async {  // cache
        println("Cache: computing result")
        delay(500)
        "cached-value"
    }

    println("Cache result: ${'$'}{cached.await()}")
}
                """.trimIndent()
            )
        }

        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }
}
