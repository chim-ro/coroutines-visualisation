package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ThreadsVsCoroutinesScenario : Scenario {
    override val info = ScenarioInfo(
        id = "threads-vs-coroutines",
        name = "Threads vs Coroutines",
        description = "Side-by-side: N threads (one per task, each blocked) vs 1 thread with coroutines (tasks interleave via suspension).",
        category = "Comparison"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: 2 tasks ──────────────────────────────────────────

    private fun buildBeginnerTimeline(): EventTimeline {
        val syncTree = node("sync-root", "main (threads)", BuilderType.RunBlocking,
            node("sync-task1", "task 1", BuilderType.Launch),
            node("sync-task2", "task 2", BuilderType.Launch)
        )

        val crTree = node("cr-root", "runBlocking", BuilderType.RunBlocking,
            node("cr-task1", "launch #1", BuilderType.Launch),
            node("cr-task2", "launch #2", BuilderType.Launch)
        )

        // Thread lanes — LEFT: threads approach
        // Main thread dispatches, then waits (blocked joining)
        // Thread-1 runs task1 (blocked by Thread.sleep)
        // Thread-2 runs task2 (blocked by Thread.sleep)
        // Both worker threads run in parallel, each blocked for 1000ms
        val totalDuration = 1200L
        val leftLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("dispatch", "dispatch", 0, 100, "active"),
                ThreadSegment("join-wait", "join()", 100, 1100, "blocked"),
                ThreadSegment("done", "done", 1100, totalDuration, "active")
            )),
            ThreadLane("Thread-1", listOf(
                ThreadSegment("sync-task1", "task 1", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-2", listOf(
                ThreadSegment("sync-task2", "task 2", 100, 1100, "blocked")
            ))
        )

        // RIGHT: coroutines approach — all on Main Thread
        val rightLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("cr-task1-start", "task 1", 0, 150, "active"),
                ThreadSegment("cr-task2-start", "task 2", 150, 300, "active"),
                ThreadSegment("suspend-all", "suspended", 300, 800, "suspended"),
                ThreadSegment("cr-task1-resume", "task 1", 800, 900, "active"),
                ThreadSegment("cr-task2-resume", "task 2", 900, 1000, "active"),
                ThreadSegment("done", "done", 1000, totalDuration, "active")
            ))
        )

        val events = listOf(
            narrative(0, "Left: each task gets its own thread — Right: all tasks share 1 thread"),
            starts(100, "Main dispatches threads", "sync-root"),
            starts(100, "runBlocking starts (samples only — production uses suspend fun main)", "cr-root"),

            starts(200, "Thread-1: task 1 starts (Thread.sleep — blocked!)", "sync-task1"),
            starts(200, "Thread-2: task 2 starts (Thread.sleep — blocked!)", "sync-task2"),

            starts(300, "CR: launch #1 starts on main thread", "cr-task1"),
            starts(400, "CR: launch #2 starts on main thread", "cr-task2"),
            suspends(500, "CR: #1 suspends (delay) — main thread free!", "cr-task1"),
            suspends(550, "CR: #2 suspends (delay) — main thread free!", "cr-task2"),

            narrative(600, "Threads: 2 worker threads sitting blocked | Coroutines: 0 threads blocked, main is free"),

            resumes(800, "CR: #1 resumes on main thread", "cr-task1"),
            resumes(850, "CR: #2 resumes on main thread", "cr-task2"),
            completing(900, "CR: #1 completing", "cr-task1"),
            completed(920, "CR: #1 completed", "cr-task1"),
            completing(940, "CR: #2 completing", "cr-task2"),
            completed(960, "CR: #2 completed", "cr-task2"),
            completing(980, "CR: runBlocking completing", "cr-root"),
            completed(1000, "CR: runBlocking completed", "cr-root"),

            completing(1050, "Threads: task 1 completing", "sync-task1"),
            completed(1060, "Threads: task 1 done", "sync-task1"),
            completing(1070, "Threads: task 2 completing", "sync-task2"),
            completed(1080, "Threads: task 2 done", "sync-task2"),
            completing(1100, "Threads: main completing", "sync-root"),
            completed(1120, "Threads: main completed", "sync-root"),

            narrative(1200, "Same speed (~1000ms) but coroutines used 1 thread vs 3!")
        )

        return timeline(
            tree = syncTree,
            secondTree = crTree,
            events = events,
            visualizationMode = "timeline",
            leftThreadLanes = leftLanes,
            rightThreadLanes = rightLanes,
            totalDurationMs = totalDuration,
            kotlinCode = """
// LEFT: Threads
fun main() {
    val t1 = thread { Thread.sleep(1000) }  // task 1 — blocks Thread-1
    val t2 = thread { Thread.sleep(1000) }  // task 2 — blocks Thread-2
    t1.join(); t2.join()
    // Fast (~1000ms) but used 3 threads!
}

// RIGHT: Coroutines
fun main() = runBlocking {
    launch { delay(1000) }  // suspends, frees main thread
    launch { delay(1000) }
    // Fast (~1000ms) AND only 1 thread!
}
            """.trimIndent()
        )
    }

    // ── Intermediate: 3 tasks ──────────────────────────────────────

    private fun buildIntermediateTimeline(): EventTimeline {
        val syncTree = node("sync-root", "main (threads)", BuilderType.RunBlocking,
            node("sync-task1", "task 1", BuilderType.Launch),
            node("sync-task2", "task 2", BuilderType.Launch),
            node("sync-task3", "task 3", BuilderType.Launch)
        )

        val crTree = node("cr-root", "runBlocking", BuilderType.RunBlocking,
            node("cr-task1", "launch #1", BuilderType.Launch),
            node("cr-task2", "launch #2", BuilderType.Launch),
            node("cr-task3", "launch #3", BuilderType.Launch)
        )

        val totalDuration = 1300L
        val leftLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("dispatch", "dispatch", 0, 100, "active"),
                ThreadSegment("join-wait", "join()", 100, 1100, "blocked"),
                ThreadSegment("done", "done", 1100, totalDuration, "active")
            )),
            ThreadLane("Thread-1", listOf(
                ThreadSegment("sync-task1", "task 1", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-2", listOf(
                ThreadSegment("sync-task2", "task 2", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-3", listOf(
                ThreadSegment("sync-task3", "task 3", 100, 1100, "blocked")
            ))
        )

        val rightLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("cr-task1-start", "task 1", 0, 150, "active"),
                ThreadSegment("cr-task2-start", "task 2", 150, 300, "active"),
                ThreadSegment("cr-task3-start", "task 3", 300, 450, "active"),
                ThreadSegment("suspend-all", "suspended", 450, 850, "suspended"),
                ThreadSegment("cr-task1-resume", "task 1", 850, 950, "active"),
                ThreadSegment("cr-task2-resume", "task 2", 950, 1050, "active"),
                ThreadSegment("cr-task3-resume", "task 3", 1050, 1150, "active"),
                ThreadSegment("done", "done", 1150, totalDuration, "active")
            ))
        )

        val events = listOf(
            narrative(0, "Left: 3 tasks → 3 threads (each blocked) — Right: 3 tasks → 1 thread (interleaved)"),
            starts(100, "Main dispatches 3 threads", "sync-root"),
            starts(100, "runBlocking starts (samples only — production uses suspend fun main)", "cr-root"),

            starts(200, "Thread-1: task 1 starts (Thread.sleep — blocked!)", "sync-task1"),
            starts(200, "Thread-2: task 2 starts (Thread.sleep — blocked!)", "sync-task2"),
            starts(200, "Thread-3: task 3 starts (Thread.sleep — blocked!)", "sync-task3"),

            starts(300, "CR: launch #1 starts on main thread", "cr-task1"),
            starts(400, "CR: launch #2 starts on main thread", "cr-task2"),
            starts(500, "CR: launch #3 starts on main thread", "cr-task3"),
            suspends(600, "CR: #1 suspends (delay) — main thread free!", "cr-task1"),
            suspends(650, "CR: #2 suspends (delay)", "cr-task2"),
            suspends(700, "CR: #3 suspends (delay)", "cr-task3"),

            narrative(750, "Threads: 3 worker threads sitting blocked | Coroutines: 0 threads blocked"),

            resumes(900, "CR: #1 resumes on main thread", "cr-task1"),
            resumes(950, "CR: #2 resumes", "cr-task2"),
            resumes(1000, "CR: #3 resumes", "cr-task3"),
            completing(1050, "CR: #1 completing", "cr-task1"),
            completed(1060, "CR: #1 completed", "cr-task1"),
            completing(1070, "CR: #2 completing", "cr-task2"),
            completed(1080, "CR: #2 completed", "cr-task2"),
            completing(1090, "CR: #3 completing", "cr-task3"),
            completed(1100, "CR: #3 completed", "cr-task3"),
            completing(1110, "CR: runBlocking completing", "cr-root"),
            completed(1120, "CR: runBlocking completed", "cr-root"),

            completing(1130, "Threads: task 1 completing", "sync-task1"),
            completed(1140, "Threads: task 1 done", "sync-task1"),
            completing(1150, "Threads: task 2 completing", "sync-task2"),
            completed(1160, "Threads: task 2 done", "sync-task2"),
            completing(1170, "Threads: task 3 completing", "sync-task3"),
            completed(1180, "Threads: task 3 done", "sync-task3"),
            completing(1200, "Threads: main completing", "sync-root"),
            completed(1220, "Threads: main completed", "sync-root"),

            narrative(1300, "Same speed (~1000ms) but coroutines used 1 thread vs 4!")
        )

        return timeline(
            tree = syncTree,
            secondTree = crTree,
            events = events,
            visualizationMode = "timeline",
            leftThreadLanes = leftLanes,
            rightThreadLanes = rightLanes,
            totalDurationMs = totalDuration,
            kotlinCode = """
// LEFT: Threads
fun main() {
    val t1 = thread { Thread.sleep(1000) }  // task 1 — blocks Thread-1
    val t2 = thread { Thread.sleep(1000) }  // task 2 — blocks Thread-2
    val t3 = thread { Thread.sleep(1000) }  // task 3 — blocks Thread-3
    t1.join(); t2.join(); t3.join()
    // Fast (~1000ms) but used 4 threads!
}

// RIGHT: Coroutines
fun main() = runBlocking {
    launch { delay(1000) }  // suspends, frees main thread
    launch { delay(1000) }
    launch { delay(1000) }
    // Fast (~1000ms) AND only 1 thread!
}
            """.trimIndent()
        )
    }

    // ── Advanced: 4 tasks + withContext(IO) ─────────────────────────

    private fun buildAdvancedTimeline(): EventTimeline {
        val syncTree = node("sync-root", "main (threads)", BuilderType.RunBlocking,
            node("sync-task1", "task 1", BuilderType.Launch),
            node("sync-task2", "task 2", BuilderType.Launch),
            node("sync-task3", "task 3", BuilderType.Launch),
            node("sync-task4", "task 4", BuilderType.Launch)
        )

        val crTree = node("cr-root", "runBlocking", BuilderType.RunBlocking,
            node("cr-task1", "launch #1", BuilderType.Launch,
                node("cr-task1-io", "withContext(IO)", BuilderType.CoroutineScope)
            ),
            node("cr-task2", "launch #2", BuilderType.Launch),
            node("cr-task3", "launch #3", BuilderType.Launch),
            node("cr-task4", "launch #4", BuilderType.Launch)
        )

        val totalDuration = 1400L
        val leftLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("dispatch", "dispatch", 0, 100, "active"),
                ThreadSegment("join-wait", "join()", 100, 1100, "blocked"),
                ThreadSegment("done", "done", 1100, totalDuration, "active")
            )),
            ThreadLane("Thread-1", listOf(
                ThreadSegment("sync-task1", "task 1", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-2", listOf(
                ThreadSegment("sync-task2", "task 2", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-3", listOf(
                ThreadSegment("sync-task3", "task 3", 100, 1100, "blocked")
            )),
            ThreadLane("Thread-4", listOf(
                ThreadSegment("sync-task4", "task 4", 100, 1100, "blocked")
            ))
        )

        // Right side: Main Thread + IO Thread (for withContext)
        val rightLanes = listOf(
            ThreadLane("Main Thread", listOf(
                ThreadSegment("cr-task1-start", "task 1", 0, 120, "active"),
                ThreadSegment("cr-task2-start", "task 2", 120, 240, "active"),
                ThreadSegment("cr-task3-start", "task 3", 240, 360, "active"),
                ThreadSegment("cr-task4-start", "task 4", 360, 480, "active"),
                ThreadSegment("suspend-all", "suspended", 480, 850, "suspended"),
                ThreadSegment("cr-task1-resume", "task 1", 850, 930, "active"),
                ThreadSegment("cr-task2-resume", "task 2", 930, 1010, "active"),
                ThreadSegment("cr-task3-resume", "task 3", 1010, 1090, "active"),
                ThreadSegment("cr-task4-resume", "task 4", 1090, 1170, "active"),
                ThreadSegment("done", "done", 1170, totalDuration, "active")
            )),
            ThreadLane("IO Thread", listOf(
                ThreadSegment("cr-task1-io", "task 1 (IO)", 480, 850, "active")
            ))
        )

        val events = listOf(
            narrative(0, "Left: 4 tasks → 4 threads (each blocked) — Right: 4 tasks → main + IO thread"),
            starts(100, "Main dispatches 4 threads", "sync-root"),
            starts(100, "runBlocking starts (samples only — production uses suspend fun main)", "cr-root"),

            starts(200, "Thread-1: task 1 (Thread.sleep — blocked!)", "sync-task1"),
            starts(200, "Thread-2: task 2 (Thread.sleep — blocked!)", "sync-task2"),
            starts(200, "Thread-3: task 3 (Thread.sleep — blocked!)", "sync-task3"),
            starts(200, "Thread-4: task 4 (Thread.sleep — blocked!)", "sync-task4"),

            starts(300, "CR: launch #1 starts on main thread", "cr-task1"),
            starts(350, "CR: launch #2 starts on main thread", "cr-task2"),
            starts(400, "CR: launch #3 starts on main thread", "cr-task3"),
            starts(450, "CR: launch #4 starts on main thread", "cr-task4"),

            starts(500, "CR: #1 enters withContext(IO) — switches to IO thread", "cr-task1-io"),
            narrative(520, "CR: task 1 switched to IO thread via withContext — main thread still free. (Note: Dispatchers.IO has a shared 64-thread limit — under heavy load use Dispatchers.IO.limitedParallelism(n) or virtual threads on JVM 21+.)"),

            suspends(550, "CR: #1 suspends on main (running on IO)", "cr-task1"),
            suspends(600, "CR: #2 suspends (delay)", "cr-task2"),
            suspends(650, "CR: #3 suspends (delay)", "cr-task3"),
            suspends(700, "CR: #4 suspends (delay)", "cr-task4"),

            narrative(750, "Threads: 4 workers blocked | Coroutines: main free, only IO thread busy for task 1"),

            completing(900, "CR: withContext(IO) completing", "cr-task1-io"),
            completed(910, "CR: withContext(IO) completed", "cr-task1-io"),
            resumes(920, "CR: #1 resumes on main", "cr-task1"),
            resumes(950, "CR: #2 resumes", "cr-task2"),
            resumes(1000, "CR: #3 resumes", "cr-task3"),
            resumes(1050, "CR: #4 resumes", "cr-task4"),

            completing(1100, "CR: #1 completing", "cr-task1"),
            completed(1110, "CR: #1 completed", "cr-task1"),
            completing(1120, "CR: #2 completing", "cr-task2"),
            completed(1130, "CR: #2 completed", "cr-task2"),
            completing(1140, "CR: #3 completing", "cr-task3"),
            completed(1150, "CR: #3 completed", "cr-task3"),
            completing(1160, "CR: #4 completing", "cr-task4"),
            completed(1170, "CR: #4 completed", "cr-task4"),
            completing(1180, "CR: runBlocking completing", "cr-root"),
            completed(1200, "CR: runBlocking completed", "cr-root"),

            completing(1210, "Threads: task 1 completing", "sync-task1"),
            completed(1220, "Threads: task 1 done", "sync-task1"),
            completing(1230, "Threads: task 2 completing", "sync-task2"),
            completed(1240, "Threads: task 2 done", "sync-task2"),
            completing(1250, "Threads: task 3 completing", "sync-task3"),
            completed(1260, "Threads: task 3 done", "sync-task3"),
            completing(1270, "Threads: task 4 completing", "sync-task4"),
            completed(1280, "Threads: task 4 done", "sync-task4"),
            completing(1300, "Threads: main completing", "sync-root"),
            completed(1320, "Threads: main completed", "sync-root"),

            narrative(1400, "Same speed — coroutines used 2 threads vs 5, and only borrowed IO thread briefly!")
        )

        return timeline(
            tree = syncTree,
            secondTree = crTree,
            events = events,
            visualizationMode = "timeline",
            leftThreadLanes = leftLanes,
            rightThreadLanes = rightLanes,
            totalDurationMs = totalDuration,
            kotlinCode = """
// LEFT: Threads
fun main() {
    val t1 = thread { Thread.sleep(1000) }  // task 1 — blocks Thread-1
    val t2 = thread { Thread.sleep(1000) }  // task 2 — blocks Thread-2
    val t3 = thread { Thread.sleep(1000) }  // task 3 — blocks Thread-3
    val t4 = thread { Thread.sleep(1000) }  // task 4 — blocks Thread-4
    t1.join(); t2.join(); t3.join(); t4.join()
    // Fast (~1000ms) but used 5 threads!
}

// RIGHT: Coroutines + withContext
fun main() = runBlocking {
    launch {
        withContext(Dispatchers.IO) {  // borrows IO thread briefly
            delay(1000)
        }
    }
    launch { delay(1000) }  // task 2
    launch { delay(1000) }  // task 3
    launch { delay(1000) }  // task 4
    // Fast (~1000ms) AND only 2 threads (main + IO)!
}
            """.trimIndent()
        )
    }
}
