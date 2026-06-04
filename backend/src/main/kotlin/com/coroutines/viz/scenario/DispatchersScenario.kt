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
        val tree = node("root", "runBlocking", BuilderType.RunBlocking,
            node("cpu-work", "launch (Default)", BuilderType.Launch),
            node("io-work", "launch (IO)", BuilderType.Launch),
            node("switcher", "launch (switcher)", BuilderType.Launch,
                node("with-context", "withContext(Default)", BuilderType.CoroutineScope)
            )
        )

        val events = listOf(
            narrative(0, "Dispatchers determine which thread(s) a coroutine runs on. Default = CPU-bound thread pool, IO = blocking I/O thread pool, Main = UI thread (Android)."),
            starts(100, "runBlocking starts on the main thread — it uses a confined dispatcher by default. (In production code, prefer `suspend fun main() = coroutineScope { ... }`; runBlocking is for samples.)", "root"),
            starts(300, "launch(Dispatchers.Default) starts on a shared CPU thread pool — optimized for computation", "cpu-work"),
            narrative(500, "Dispatchers.Default uses a thread pool sized to the number of CPU cores. Best for CPU-intensive work like sorting, parsing, or calculations."),
            starts(700, "launch(Dispatchers.IO) starts on the I/O thread pool — optimized for blocking operations", "io-work"),
            narrative(900, "Dispatchers.IO uses a larger thread pool (default 64 threads, shared across the whole app). Watch out: under heavy load this limit is shared by every IO-bound coroutine, which can starve the pool. Modern alternatives: `Dispatchers.IO.limitedParallelism(n)` for per-service limits, or Project Loom (JVM 21+) for virtual threads."),
            starts(1100, "The switcher launch starts on the inherited dispatcher (main thread from runBlocking)", "switcher"),
            narrative(1300, "The switcher coroutine needs to do CPU work but is running on the main thread. It uses withContext to switch dispatchers without launching a new coroutine."),
            narrative(1400, "Note: withContext is a suspending function, NOT a coroutine builder. It doesn't create a new Job — it just changes the context for a block. The node below is drawn that way for clarity, but it's really a dispatcher switch within the switcher coroutine."),
            starts(1500, "withContext(Dispatchers.Default) — switches to the CPU thread pool, suspending the parent coroutine", "with-context"),
            narrative(1700, "withContext is a suspending function, not a coroutine builder. It switches context (e.g. dispatcher) for a block of code and returns the result. The calling coroutine suspends until the block completes."),
            completing(1900, "CPU-bound work on Dispatchers.Default finishes", "cpu-work"),
            completed(2100, "CPU-bound launch fully completed", "cpu-work"),
            completing(2300, "I/O work on Dispatchers.IO finishes (e.g. file read complete)", "io-work"),
            completed(2500, "I/O launch fully completed", "io-work"),
            completing(2700, "withContext block finishes — result is returned and dispatcher switches back to the original", "with-context"),
            completed(2900, "withContext fully completed — switcher resumes on its original dispatcher", "with-context"),
            completing(3100, "Switcher launch completes after withContext returns", "switcher"),
            completed(3300, "Switcher launch fully completed", "switcher"),
            completing(3500, "All children complete — runBlocking finishes", "root"),
            completed(3700, "runBlocking fully completed", "root"),
            narrative(3900, "Key insight: Use Dispatchers.Default for CPU work, Dispatchers.IO for blocking I/O, and withContext to switch dispatchers mid-coroutine without creating a new coroutine.")
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

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()

        "beginner" -> {
            val tree = node("root", "runBlocking", BuilderType.RunBlocking,
                node("cpu-work", "launch (Default)", BuilderType.Launch)
            )

            val events = listOf(
                starts(100, "runBlocking starts on the main thread. (In real apps, prefer `suspend fun main()`; runBlocking is for samples.)", "root"),
                narrative(300, "A dispatcher determines which thread or thread pool a coroutine runs on. Think of it as assigning a worker to a specific department."),
                starts(500, "launch(Dispatchers.Default) starts on the shared CPU thread pool", "cpu-work"),
                narrative(700, "Dispatchers.Default provides a thread pool sized to the number of CPU cores. It is optimized for CPU-intensive tasks like sorting or mathematical computations."),
                completing(900, "CPU-bound work on Dispatchers.Default finishes", "cpu-work"),
                completed(1100, "CPU-bound launch fully completed", "cpu-work"),
                completing(1300, "All children complete — runBlocking finishes", "root"),
                completed(1500, "runBlocking fully completed", "root")
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

            timeline(
                tree = tree,
                events = events,
                kotlinCode = kotlinCode
            )
        }

        "advanced" -> {
            val tree = node("root", "runBlocking", BuilderType.RunBlocking,
                node("cpu-work", "launch (Default)", BuilderType.Launch),
                node("io-work", "launch (IO)", BuilderType.Launch),
                node("ui-work", "launch (Main (concept))", BuilderType.Launch),
                node("switcher", "launch (switcher)", BuilderType.Launch,
                    node("ctx-default", "withContext(Default)", BuilderType.CoroutineScope),
                    node("ctx-io", "withContext(IO)", BuilderType.CoroutineScope)
                )
            )

            val events = listOf(
                narrative(0, "Dispatchers control threading: Default = CPU pool (cores-sized), IO = blocking I/O pool (64 threads), Main = UI thread (Android/Desktop). withContext switches dispatchers without launching a new coroutine."),
                starts(100, "runBlocking starts on the main thread — confined dispatcher by default. (Production code: prefer `suspend fun main() = coroutineScope { ... }`.)", "root"),
                starts(300, "launch(Dispatchers.Default) starts on the shared CPU thread pool", "cpu-work"),
                narrative(500, "Dispatchers.Default uses a thread pool sized to the number of CPU cores. Best for CPU-intensive work like sorting, parsing, JSON serialization, or calculations."),
                starts(700, "launch(Dispatchers.IO) starts on the I/O thread pool — optimized for blocking operations", "io-work"),
                narrative(900, "Dispatchers.IO uses a larger thread pool (default 64 threads, shared app-wide) — but that shared limit is easy to saturate under load. Modern preference: `Dispatchers.IO.limitedParallelism(n)` per service for isolation, or Project Loom virtual threads (JVM 21+) for unlimited cheap blocking."),
                starts(1100, "launch(Dispatchers.Main) starts on the UI thread — used on Android and Desktop platforms", "ui-work"),
                narrative(1300, "Dispatchers.Main confines execution to the main/UI thread. Essential for updating UI components on Android. Not available in plain JVM unless a Main dispatcher is installed (e.g., kotlinx-coroutines-swing)."),
                starts(1500, "The switcher launch starts on the inherited dispatcher (main thread from runBlocking)", "switcher"),
                narrative(1700, "The switcher coroutine will use withContext twice sequentially — first switching to Default for CPU work, then to IO for a blocking call. Each withContext suspends the caller until the block completes."),
                narrative(1800, "Note: withContext is a suspending function, NOT a coroutine builder. It doesn't create a new Job — it just changes the context for a block. The nodes below are drawn that way for clarity, but they're really just dispatcher switches within the switcher coroutine."),
                starts(1900, "withContext(Dispatchers.Default) — switches to the CPU thread pool, suspending the switcher coroutine", "ctx-default"),
                completing(2100, "withContext(Default) block finishes — result returned, dispatcher switches back", "ctx-default"),
                completed(2300, "withContext(Default) fully completed", "ctx-default"),
                starts(2500, "withContext(Dispatchers.IO) — switches to the IO thread pool for a blocking operation", "ctx-io"),
                completing(2700, "withContext(IO) block finishes — blocking I/O complete, dispatcher switches back", "ctx-io"),
                completed(2900, "withContext(IO) fully completed — switcher resumes on its original dispatcher", "ctx-io"),
                completing(3100, "CPU-bound work on Dispatchers.Default finishes", "cpu-work"),
                completed(3300, "CPU-bound launch fully completed", "cpu-work"),
                completing(3500, "I/O work on Dispatchers.IO finishes", "io-work"),
                completed(3700, "I/O launch fully completed", "io-work"),
                completing(3900, "UI work on Dispatchers.Main completes", "ui-work"),
                completed(4100, "UI launch fully completed", "ui-work"),
                completing(4300, "Switcher launch completes after both withContext calls return", "switcher"),
                completed(4500, "Switcher launch fully completed", "switcher"),
                completing(4700, "All children complete — runBlocking finishes", "root"),
                completed(4900, "runBlocking fully completed", "root"),
                narrative(5100, "Key insight: Each dispatcher is optimized for a specific workload. Use Default for CPU, IO for blocking I/O, Main for UI updates. withContext lets you switch dispatchers sequentially within a single coroutine — no new coroutine is created.")
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

            timeline(
                tree = tree,
                events = events,
                kotlinCode = kotlinCode
            )
        }

        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }
}
