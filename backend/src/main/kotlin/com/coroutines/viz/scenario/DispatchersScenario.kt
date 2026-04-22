package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class DispatchersScenario : Scenario {
    override val info = ScenarioInfo(
        id = "dispatchers",
        name = "Dispatchers & withContext",
        description = "Demonstrates Dispatchers.Default for CPU work, Dispatchers.IO for blocking I/O, and withContext for switching dispatchers mid-coroutine.",
        category = "Advanced"
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
                    id = "cpu-work",
                    displayName = "launch (Default)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "io-work",
                    displayName = "launch (IO)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "switcher",
                    displayName = "launch (switcher)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "with-context",
                            displayName = "withContext(Default)",
                            builder = BuilderType.CoroutineScope,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(
                delayMs = 0,
                description = "Dispatchers determine which thread(s) a coroutine runs on. Default = CPU-bound thread pool, IO = blocking I/O thread pool, Main = UI thread (Android)."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "runBlocking starts on the main thread — it uses a confined dispatcher by default",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 300,
                description = "launch(Dispatchers.Default) starts on a shared CPU thread pool — optimized for computation",
                nodeId = "cpu-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 500,
                description = "Dispatchers.Default uses a thread pool sized to the number of CPU cores. Best for CPU-intensive work like sorting, parsing, or calculations."
            ),
            StateChangeEvent(
                delayMs = 700,
                description = "launch(Dispatchers.IO) starts on the I/O thread pool — optimized for blocking operations",
                nodeId = "io-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 900,
                description = "Dispatchers.IO uses a larger thread pool (default 64 threads). Best for blocking I/O: file access, network calls, database queries."
            ),
            StateChangeEvent(
                delayMs = 1100,
                description = "The switcher launch starts on the inherited dispatcher (main thread from runBlocking)",
                nodeId = "switcher",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1300,
                description = "The switcher coroutine needs to do CPU work but is running on the main thread. It uses withContext to switch dispatchers without launching a new coroutine."
            ),
            StateChangeEvent(
                delayMs = 1500,
                description = "withContext(Dispatchers.Default) — switches to the CPU thread pool, suspending the parent coroutine",
                nodeId = "with-context",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1700,
                description = "withContext is a suspending function, not a coroutine builder. It switches context (e.g. dispatcher) for a block of code and returns the result. The calling coroutine suspends until the block completes."
            ),
            StateChangeEvent(
                delayMs = 1900,
                description = "CPU-bound work on Dispatchers.Default finishes",
                nodeId = "cpu-work",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2100,
                description = "CPU-bound launch fully completed",
                nodeId = "cpu-work",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2300,
                description = "I/O work on Dispatchers.IO finishes (e.g. file read complete)",
                nodeId = "io-work",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2500,
                description = "I/O launch fully completed",
                nodeId = "io-work",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2700,
                description = "withContext block finishes — result is returned and dispatcher switches back to the original",
                nodeId = "with-context",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2900,
                description = "withContext fully completed — switcher resumes on its original dispatcher",
                nodeId = "with-context",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 3100,
                description = "Switcher launch completes after withContext returns",
                nodeId = "switcher",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3300,
                description = "Switcher launch fully completed",
                nodeId = "switcher",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 3500,
                description = "All children complete — runBlocking finishes",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3700,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 3900,
                description = "Key insight: Use Dispatchers.Default for CPU work, Dispatchers.IO for blocking I/O, and withContext to switch dispatchers mid-coroutine without creating a new coroutine."
            )
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            fun main() = runBlocking {
                // launch on Dispatchers.Default — CPU-bound work
                launch(Dispatchers.Default) {
                    println("Default: ${'$'}{Thread.currentThread().name}")
                    // Heavy computation (sorting, parsing, etc.)
                    val result = (1..1_000_000).sumOf { it.toLong() }
                    println("Computed: ${'$'}result")
                }

                // launch on Dispatchers.IO — blocking I/O
                launch(Dispatchers.IO) {
                    println("IO: ${'$'}{Thread.currentThread().name}")
                    // Simulates blocking I/O (file read, network call)
                    delay(1000L)
                    println("I/O complete")
                }

                // launch inherits dispatcher, then switches with withContext
                launch {
                    println("Before: ${'$'}{Thread.currentThread().name}")

                    val result = withContext(Dispatchers.Default) {
                        println("Inside: ${'$'}{Thread.currentThread().name}")
                        // CPU work on Default pool
                        (1..100).sumOf { it.toLong() }
                    }

                    // Back on original dispatcher
                    println("After: ${'$'}{Thread.currentThread().name}")
                    println("Result: ${'$'}result")
                }
            }
        """.trimIndent()

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
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
                        id = "cpu-work",
                        displayName = "launch (Default)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    )
                )
            )

            val events = listOf(
                StateChangeEvent(
                    delayMs = 100,
                    description = "runBlocking starts on the main thread",
                    nodeId = "root",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 300,
                    description = "A dispatcher determines which thread or thread pool a coroutine runs on. Think of it as assigning a worker to a specific department."
                ),
                StateChangeEvent(
                    delayMs = 500,
                    description = "launch(Dispatchers.Default) starts on the shared CPU thread pool",
                    nodeId = "cpu-work",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 700,
                    description = "Dispatchers.Default provides a thread pool sized to the number of CPU cores. It is optimized for CPU-intensive tasks like sorting or mathematical computations."
                ),
                StateChangeEvent(
                    delayMs = 900,
                    description = "CPU-bound work on Dispatchers.Default finishes",
                    nodeId = "cpu-work",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 1100,
                    description = "CPU-bound launch fully completed",
                    nodeId = "cpu-work",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 1300,
                    description = "All children complete — runBlocking finishes",
                    nodeId = "root",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 1500,
                    description = "runBlocking fully completed",
                    nodeId = "root",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                )
            )

            val kotlinCode = """
                import kotlinx.coroutines.*

                fun main() = runBlocking {
                    // launch on Dispatchers.Default — CPU-bound work
                    launch(Dispatchers.Default) {
                        println("Running on: ${'$'}{Thread.currentThread().name}")
                        val result = (1..1_000_000).sumOf { it.toLong() }
                        println("Computed: ${'$'}result")
                    }
                }
            """.trimIndent()

            EventTimeline(
                scenarioName = info.name,
                tree = tree,
                events = events,
                kotlinCode = kotlinCode
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
                        id = "cpu-work",
                        displayName = "launch (Default)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "io-work",
                        displayName = "launch (IO)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "ui-work",
                        displayName = "launch (Main (concept))",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New
                    ),
                    CoroutineNode(
                        id = "switcher",
                        displayName = "launch (switcher)",
                        builder = BuilderType.Launch,
                        jobType = JobType.Job,
                        initialState = JobState.New,
                        children = listOf(
                            CoroutineNode(
                                id = "ctx-default",
                                displayName = "withContext(Default)",
                                builder = BuilderType.CoroutineScope,
                                jobType = JobType.Job,
                                initialState = JobState.New
                            ),
                            CoroutineNode(
                                id = "ctx-io",
                                displayName = "withContext(IO)",
                                builder = BuilderType.CoroutineScope,
                                jobType = JobType.Job,
                                initialState = JobState.New
                            )
                        )
                    )
                )
            )

            val events = listOf(
                NarrativeEvent(
                    delayMs = 0,
                    description = "Dispatchers control threading: Default = CPU pool (cores-sized), IO = blocking I/O pool (64 threads), Main = UI thread (Android/Desktop). withContext switches dispatchers without launching a new coroutine."
                ),
                StateChangeEvent(
                    delayMs = 100,
                    description = "runBlocking starts on the main thread — it uses a confined dispatcher by default",
                    nodeId = "root",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                StateChangeEvent(
                    delayMs = 300,
                    description = "launch(Dispatchers.Default) starts on the shared CPU thread pool",
                    nodeId = "cpu-work",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 500,
                    description = "Dispatchers.Default uses a thread pool sized to the number of CPU cores. Best for CPU-intensive work like sorting, parsing, JSON serialization, or calculations."
                ),
                StateChangeEvent(
                    delayMs = 700,
                    description = "launch(Dispatchers.IO) starts on the I/O thread pool — optimized for blocking operations",
                    nodeId = "io-work",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 900,
                    description = "Dispatchers.IO uses a larger thread pool (default 64 threads). Best for blocking I/O: file access, network calls, database queries. It shares threads with Default but can grow beyond the core count."
                ),
                StateChangeEvent(
                    delayMs = 1100,
                    description = "launch(Dispatchers.Main) starts on the UI thread — used on Android and Desktop platforms",
                    nodeId = "ui-work",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 1300,
                    description = "Dispatchers.Main confines execution to the main/UI thread. Essential for updating UI components on Android. Not available in plain JVM unless a Main dispatcher is installed (e.g., kotlinx-coroutines-swing)."
                ),
                StateChangeEvent(
                    delayMs = 1500,
                    description = "The switcher launch starts on the inherited dispatcher (main thread from runBlocking)",
                    nodeId = "switcher",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                NarrativeEvent(
                    delayMs = 1700,
                    description = "The switcher coroutine will use withContext twice sequentially — first switching to Default for CPU work, then to IO for a blocking call. Each withContext suspends the caller until the block completes."
                ),
                StateChangeEvent(
                    delayMs = 1900,
                    description = "withContext(Dispatchers.Default) — switches to the CPU thread pool, suspending the switcher coroutine",
                    nodeId = "ctx-default",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                StateChangeEvent(
                    delayMs = 2100,
                    description = "withContext(Default) block finishes — result returned, dispatcher switches back",
                    nodeId = "ctx-default",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 2300,
                    description = "withContext(Default) fully completed",
                    nodeId = "ctx-default",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 2500,
                    description = "withContext(Dispatchers.IO) — switches to the IO thread pool for a blocking operation",
                    nodeId = "ctx-io",
                    fromState = JobState.New,
                    toState = JobState.Active
                ),
                StateChangeEvent(
                    delayMs = 2700,
                    description = "withContext(IO) block finishes — blocking I/O complete, dispatcher switches back",
                    nodeId = "ctx-io",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 2900,
                    description = "withContext(IO) fully completed — switcher resumes on its original dispatcher",
                    nodeId = "ctx-io",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 3100,
                    description = "CPU-bound work on Dispatchers.Default finishes",
                    nodeId = "cpu-work",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 3300,
                    description = "CPU-bound launch fully completed",
                    nodeId = "cpu-work",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 3500,
                    description = "I/O work on Dispatchers.IO finishes",
                    nodeId = "io-work",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 3700,
                    description = "I/O launch fully completed",
                    nodeId = "io-work",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 3900,
                    description = "UI work on Dispatchers.Main completes",
                    nodeId = "ui-work",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 4100,
                    description = "UI launch fully completed",
                    nodeId = "ui-work",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 4300,
                    description = "Switcher launch completes after both withContext calls return",
                    nodeId = "switcher",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 4500,
                    description = "Switcher launch fully completed",
                    nodeId = "switcher",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                StateChangeEvent(
                    delayMs = 4700,
                    description = "All children complete — runBlocking finishes",
                    nodeId = "root",
                    fromState = JobState.Active,
                    toState = JobState.Completing
                ),
                StateChangeEvent(
                    delayMs = 4900,
                    description = "runBlocking fully completed",
                    nodeId = "root",
                    fromState = JobState.Completing,
                    toState = JobState.Completed
                ),
                NarrativeEvent(
                    delayMs = 5100,
                    description = "Key insight: Each dispatcher is optimized for a specific workload. Use Default for CPU, IO for blocking I/O, Main for UI updates. withContext lets you switch dispatchers sequentially within a single coroutine — no new coroutine is created."
                )
            )

            val kotlinCode = """
                import kotlinx.coroutines.*

                fun main() = runBlocking {
                    // CPU-bound work on Default dispatcher
                    launch(Dispatchers.Default) {
                        println("Default: ${'$'}{Thread.currentThread().name}")
                        val result = (1..1_000_000).sumOf { it.toLong() }
                        println("Computed: ${'$'}result")
                    }

                    // Blocking I/O on IO dispatcher
                    launch(Dispatchers.IO) {
                        println("IO: ${'$'}{Thread.currentThread().name}")
                        delay(1000L) // Simulates blocking I/O
                        println("I/O complete")
                    }

                    // UI work on Main dispatcher (Android/Desktop)
                    launch(Dispatchers.Main) {
                        println("Main: ${'$'}{Thread.currentThread().name}")
                        // Update UI components here
                        println("UI updated")
                    }

                    // Sequential dispatcher switching with withContext
                    launch {
                        println("Switcher on: ${'$'}{Thread.currentThread().name}")

                        // Switch to Default for CPU work
                        val cpuResult = withContext(Dispatchers.Default) {
                            println("  Default: ${'$'}{Thread.currentThread().name}")
                            (1..100).sumOf { it.toLong() }
                        }
                        println("CPU result: ${'$'}cpuResult")

                        // Switch to IO for blocking call
                        val ioResult = withContext(Dispatchers.IO) {
                            println("  IO: ${'$'}{Thread.currentThread().name}")
                            // Simulate reading a file
                            "file contents"
                        }
                        println("IO result: ${'$'}ioResult")

                        // Back on original dispatcher
                        println("Back on: ${'$'}{Thread.currentThread().name}")
                    }
                }
            """.trimIndent()

            EventTimeline(
                scenarioName = info.name,
                tree = tree,
                events = events,
                kotlinCode = kotlinCode
            )
        }

        else -> buildTimeline()
    }
}
