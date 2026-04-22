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
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "fetcher",
                    displayName = "launch (fetcher)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "processor",
                    displayName = "launch (processor)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "logger",
                    displayName = "launch (logger)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            // Phase 1: Setup
            NarrativeEvent(0, "All three coroutines share a single thread (main). Let's see how suspension enables concurrency."),
            StateChangeEvent(200, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(600, "Fetcher starts — it will call a suspending network function", "fetcher", JobState.New, JobState.Active),

            // Phase 2: Fetcher suspends
            NarrativeEvent(1200, "Fetcher calls delay(1000) — simulating a network request. The coroutine SUSPENDS."),
            StateChangeEvent(1400, "Fetcher suspends — the thread is now FREE", "fetcher", JobState.Active, JobState.Suspended),
            NarrativeEvent(1600, "Key insight: the thread is not blocked! It's released back to the dispatcher."),

            // Phase 3: Other coroutines run on the freed thread
            NarrativeEvent(2000, "With the thread free, the dispatcher can run other coroutines."),
            StateChangeEvent(2200, "Processor gets the thread and starts running", "processor", JobState.New, JobState.Active),
            StateChangeEvent(2800, "Logger also gets a turn on the thread", "logger", JobState.New, JobState.Active),
            NarrativeEvent(3200, "Processor and Logger are doing real work while Fetcher sleeps."),

            // Phase 4: Other coroutines complete their work
            StateChangeEvent(3800, "Processor finishes its work", "processor", JobState.Active, JobState.Completing),
            StateChangeEvent(4100, "Processor completed", "processor", JobState.Completing, JobState.Completed),
            StateChangeEvent(4500, "Logger finishes its work", "logger", JobState.Active, JobState.Completing),
            StateChangeEvent(4800, "Logger completed", "logger", JobState.Completing, JobState.Completed),

            // Phase 5: Fetcher resumes — the aha moment
            NarrativeEvent(5400, "delay(1000) expires — the dispatcher RESUMES the fetcher coroutine."),
            StateChangeEvent(5700, "Fetcher RESUMES — picks up exactly where it left off", "fetcher", JobState.Suspended, JobState.Active),
            NarrativeEvent(6100, "This is the magic: no threads were blocked. Suspension is cooperative, not preemptive."),

            // Phase 6: Everyone completes
            StateChangeEvent(6600, "Fetcher finishes processing the response", "fetcher", JobState.Active, JobState.Completing),
            StateChangeEvent(6900, "Fetcher completed", "fetcher", JobState.Completing, JobState.Completed),
            NarrativeEvent(7200, "All children are done — root can complete."),
            StateChangeEvent(7500, "Root enters Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(7800, "Root completed — one thread served all three coroutines via suspension", "root", JobState.Completing, JobState.Completed)
        )

        return EventTimeline(
            scenarioName = info.name,
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
            val tree = CoroutineNode(
                id = "root",
                displayName = "runBlocking",
                builder = BuilderType.RunBlocking,
                jobType = JobType.Job,
                initialState = JobState.New,
                children = listOf(
                    CoroutineNode(
                        id = "worker",
                        displayName = "launch (worker)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    )
                )
            )

            val events = listOf(
                // Phase 1: Both start
                NarrativeEvent(0, "A single coroutine demonstrates suspension. Watch how delay() pauses without blocking the thread."),
                StateChangeEvent(200, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),
                StateChangeEvent(600, "Worker coroutine starts", "worker", JobState.New, JobState.Active),

                // Phase 2: Worker suspends
                NarrativeEvent(1200, "Worker calls delay(500) — it SUSPENDS. The underlying thread is not blocked."),
                StateChangeEvent(1400, "Worker suspends on delay()", "worker", JobState.Active, JobState.Suspended),
                NarrativeEvent(1800, "The thread is free! In a real app, other coroutines could run now."),

                // Phase 3: Worker resumes
                NarrativeEvent(2400, "delay(500) expires — the dispatcher resumes the worker."),
                StateChangeEvent(2600, "Worker RESUMES and continues execution", "worker", JobState.Suspended, JobState.Active),

                // Phase 4: Completion
                StateChangeEvent(3200, "Worker finishes its work", "worker", JobState.Active, JobState.Completing),
                StateChangeEvent(3500, "Worker completed", "worker", JobState.Completing, JobState.Completed),
                NarrativeEvent(3800, "Child is done — root can now complete."),
                StateChangeEvent(4100, "Root enters Completing", "root", JobState.Active, JobState.Completing),
                StateChangeEvent(4400, "Root completed", "root", JobState.Completing, JobState.Completed)
            )

            EventTimeline(
                scenarioName = info.name,
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
            val tree = CoroutineNode(
                id = "root",
                displayName = "runBlocking",
                builder = BuilderType.RunBlocking,
                jobType = JobType.Job,
                initialState = JobState.New,
                children = listOf(
                    CoroutineNode(
                        id = "fetcher",
                        displayName = "launch (fetcher)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "processor",
                        displayName = "launch (processor)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "logger",
                        displayName = "launch (logger)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "cache",
                        displayName = "async (cache)",
                        builder = BuilderType.Async,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    )
                )
            )

            val events = listOf(
                // Phase 1: All coroutines start
                NarrativeEvent(0, "Four coroutines share a single thread. Multiple suspend/resume cycles show true cooperative multitasking."),
                StateChangeEvent(200, "Root coroutine becomes Active", "root", JobState.New, JobState.Active),
                StateChangeEvent(500, "Fetcher starts — will make two network calls", "fetcher", JobState.New, JobState.Active),
                StateChangeEvent(800, "Processor starts", "processor", JobState.New, JobState.Active),
                StateChangeEvent(1100, "Logger starts", "logger", JobState.New, JobState.Active),
                StateChangeEvent(1400, "Cache async starts — will return a Deferred result", "cache", JobState.New, JobState.Active),

                // Phase 2: Fetcher suspends (first time)
                NarrativeEvent(1800, "Fetcher hits its first delay() — first network call. Thread is released."),
                StateChangeEvent(2000, "Fetcher suspends on first network call", "fetcher", JobState.Active, JobState.Suspended),

                // Phase 3: Processor runs then suspends
                NarrativeEvent(2400, "With fetcher suspended, processor gets the thread and does work."),
                StateChangeEvent(2800, "Processor suspends while waiting for data", "processor", JobState.Active, JobState.Suspended),

                // Phase 4: Logger runs, cache runs
                NarrativeEvent(3200, "Thread bounces to logger, then cache — cooperative scheduling in action."),
                StateChangeEvent(3400, "Logger writes first batch of logs", "logger", JobState.Active, JobState.Active),
                StateChangeEvent(3800, "Cache computes and stores result", "cache", JobState.Active, JobState.Active),

                // Phase 5: Fetcher resumes and suspends again (second network call)
                NarrativeEvent(4200, "Fetcher's first delay() expires — it resumes for its second network call."),
                StateChangeEvent(4400, "Fetcher RESUMES from first suspension", "fetcher", JobState.Suspended, JobState.Active),
                NarrativeEvent(4800, "Fetcher immediately hits another delay() — second network call. Suspends again!"),
                StateChangeEvent(5000, "Fetcher suspends AGAIN on second network call", "fetcher", JobState.Active, JobState.Suspended),

                // Phase 6: Cache completes
                NarrativeEvent(5400, "Cache finishes its async computation and delivers the Deferred result."),
                StateChangeEvent(5600, "Cache enters Completing", "cache", JobState.Active, JobState.Completing),
                StateChangeEvent(5800, "Cache completed — result is available via await()", "cache", JobState.Completing, JobState.Completed),

                // Phase 7: Logger suspends
                NarrativeEvent(6200, "Logger needs to flush — it suspends to wait for I/O."),
                StateChangeEvent(6400, "Logger suspends on I/O flush", "logger", JobState.Active, JobState.Suspended),

                // Phase 8: Processor resumes and completes
                NarrativeEvent(6800, "Processor's data arrived — it resumes and finishes."),
                StateChangeEvent(7000, "Processor resumes", "processor", JobState.Suspended, JobState.Active),
                StateChangeEvent(7400, "Processor enters Completing", "processor", JobState.Active, JobState.Completing),
                StateChangeEvent(7600, "Processor completed", "processor", JobState.Completing, JobState.Completed),

                // Phase 9: Fetcher resumes (second time) and completes
                NarrativeEvent(8000, "Fetcher's second delay() expires — it resumes for the last time."),
                StateChangeEvent(8200, "Fetcher RESUMES from second suspension", "fetcher", JobState.Suspended, JobState.Active),
                StateChangeEvent(8600, "Fetcher enters Completing", "fetcher", JobState.Active, JobState.Completing),
                StateChangeEvent(8800, "Fetcher completed — both network calls done", "fetcher", JobState.Completing, JobState.Completed),

                // Phase 10: Logger resumes and completes
                StateChangeEvent(9200, "Logger resumes after I/O flush", "logger", JobState.Suspended, JobState.Active),
                StateChangeEvent(9600, "Logger enters Completing", "logger", JobState.Active, JobState.Completing),
                StateChangeEvent(9800, "Logger completed", "logger", JobState.Completing, JobState.Completed),

                // Phase 11: Root completes
                NarrativeEvent(10200, "All four children are done. One thread handled everything — suspension made it possible."),
                StateChangeEvent(10500, "Root enters Completing", "root", JobState.Active, JobState.Completing),
                StateChangeEvent(10800, "Root completed — thread reuse via multiple suspend/resume cycles", "root", JobState.Completing, JobState.Completed)
            )

            EventTimeline(
                scenarioName = info.name,
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

        else -> buildTimeline()
    }
}
