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
        else -> buildIntermediateTimeline()
    }

    // ── Beginner: 2 tasks ──────────────────────────────────────────

    private fun buildBeginnerTimeline(): EventTimeline {
        val syncTree = CoroutineNode(
            id = "sync-root", displayName = "main (threads)", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "sync-task1", displayName = "task 1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task2", displayName = "task 2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val crTree = CoroutineNode(
            id = "cr-root", displayName = "runBlocking", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "cr-task1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cr-task2", displayName = "launch #2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
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
            NarrativeEvent(0, "Left: each task gets its own thread — Right: all tasks share 1 thread"),
            StateChangeEvent(100, "Main dispatches threads", "sync-root", JobState.New, JobState.Active),
            StateChangeEvent(100, "runBlocking starts", "cr-root", JobState.New, JobState.Active),

            StateChangeEvent(200, "Thread-1: task 1 starts (Thread.sleep — blocked!)", "sync-task1", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-2: task 2 starts (Thread.sleep — blocked!)", "sync-task2", JobState.New, JobState.Active),

            StateChangeEvent(300, "CR: launch #1 starts on main thread", "cr-task1", JobState.New, JobState.Active),
            StateChangeEvent(400, "CR: launch #2 starts on main thread", "cr-task2", JobState.New, JobState.Active),
            StateChangeEvent(500, "CR: #1 suspends (delay) — main thread free!", "cr-task1", JobState.Active, JobState.Suspended),
            StateChangeEvent(550, "CR: #2 suspends (delay) — main thread free!", "cr-task2", JobState.Active, JobState.Suspended),

            NarrativeEvent(600, "Threads: 2 worker threads sitting blocked | Coroutines: 0 threads blocked, main is free"),

            StateChangeEvent(800, "CR: #1 resumes on main thread", "cr-task1", JobState.Suspended, JobState.Active),
            StateChangeEvent(850, "CR: #2 resumes on main thread", "cr-task2", JobState.Suspended, JobState.Active),
            StateChangeEvent(900, "CR: #1 completed", "cr-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(950, "CR: #2 completed", "cr-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1000, "CR: runBlocking completed", "cr-root", JobState.Active, JobState.Completed),

            StateChangeEvent(1050, "Threads: task 1 done", "sync-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(1050, "Threads: task 2 done", "sync-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: main completed", "sync-root", JobState.Active, JobState.Completed),

            NarrativeEvent(1200, "Same speed (~1000ms) but coroutines used 1 thread vs 3!")
        )

        return EventTimeline(
            scenarioName = info.name,
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
        val syncTree = CoroutineNode(
            id = "sync-root", displayName = "main (threads)", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "sync-task1", displayName = "task 1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task2", displayName = "task 2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task3", displayName = "task 3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val crTree = CoroutineNode(
            id = "cr-root", displayName = "runBlocking", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "cr-task1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cr-task2", displayName = "launch #2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cr-task3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
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
            NarrativeEvent(0, "Left: 3 tasks → 3 threads (each blocked) — Right: 3 tasks → 1 thread (interleaved)"),
            StateChangeEvent(100, "Main dispatches 3 threads", "sync-root", JobState.New, JobState.Active),
            StateChangeEvent(100, "runBlocking starts", "cr-root", JobState.New, JobState.Active),

            StateChangeEvent(200, "Thread-1: task 1 starts (Thread.sleep — blocked!)", "sync-task1", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-2: task 2 starts (Thread.sleep — blocked!)", "sync-task2", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-3: task 3 starts (Thread.sleep — blocked!)", "sync-task3", JobState.New, JobState.Active),

            StateChangeEvent(300, "CR: launch #1 starts on main thread", "cr-task1", JobState.New, JobState.Active),
            StateChangeEvent(400, "CR: launch #2 starts on main thread", "cr-task2", JobState.New, JobState.Active),
            StateChangeEvent(500, "CR: launch #3 starts on main thread", "cr-task3", JobState.New, JobState.Active),
            StateChangeEvent(600, "CR: #1 suspends (delay) — main thread free!", "cr-task1", JobState.Active, JobState.Suspended),
            StateChangeEvent(650, "CR: #2 suspends (delay)", "cr-task2", JobState.Active, JobState.Suspended),
            StateChangeEvent(700, "CR: #3 suspends (delay)", "cr-task3", JobState.Active, JobState.Suspended),

            NarrativeEvent(750, "Threads: 3 worker threads sitting blocked | Coroutines: 0 threads blocked"),

            StateChangeEvent(900, "CR: #1 resumes on main thread", "cr-task1", JobState.Suspended, JobState.Active),
            StateChangeEvent(950, "CR: #2 resumes", "cr-task2", JobState.Suspended, JobState.Active),
            StateChangeEvent(1000, "CR: #3 resumes", "cr-task3", JobState.Suspended, JobState.Active),
            StateChangeEvent(1050, "CR: #1 completed", "cr-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(1080, "CR: #2 completed", "cr-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1110, "CR: #3 completed", "cr-task3", JobState.Active, JobState.Completed),
            StateChangeEvent(1150, "CR: runBlocking completed", "cr-root", JobState.Active, JobState.Completed),

            StateChangeEvent(1100, "Threads: all tasks done", "sync-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: task 2 done", "sync-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: task 3 done", "sync-task3", JobState.Active, JobState.Completed),
            StateChangeEvent(1150, "Threads: main completed", "sync-root", JobState.Active, JobState.Completed),

            NarrativeEvent(1300, "Same speed (~1000ms) but coroutines used 1 thread vs 4!")
        )

        return EventTimeline(
            scenarioName = info.name,
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
        val syncTree = CoroutineNode(
            id = "sync-root", displayName = "main (threads)", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(id = "sync-task1", displayName = "task 1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task2", displayName = "task 2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task3", displayName = "task 3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "sync-task4", displayName = "task 4", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
        )

        val crTree = CoroutineNode(
            id = "cr-root", displayName = "runBlocking", builder = BuilderType.RunBlocking,
            jobType = JobType.Job, initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "cr-task1", displayName = "launch #1", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(id = "cr-task1-io", displayName = "withContext(IO)", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
                    )
                ),
                CoroutineNode(id = "cr-task2", displayName = "launch #2", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cr-task3", displayName = "launch #3", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New),
                CoroutineNode(id = "cr-task4", displayName = "launch #4", builder = BuilderType.Launch, jobType = JobType.Job, initialState = JobState.New)
            )
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
            NarrativeEvent(0, "Left: 4 tasks → 4 threads (each blocked) — Right: 4 tasks → main + IO thread"),
            StateChangeEvent(100, "Main dispatches 4 threads", "sync-root", JobState.New, JobState.Active),
            StateChangeEvent(100, "runBlocking starts", "cr-root", JobState.New, JobState.Active),

            StateChangeEvent(200, "Thread-1: task 1 (Thread.sleep — blocked!)", "sync-task1", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-2: task 2 (Thread.sleep — blocked!)", "sync-task2", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-3: task 3 (Thread.sleep — blocked!)", "sync-task3", JobState.New, JobState.Active),
            StateChangeEvent(200, "Thread-4: task 4 (Thread.sleep — blocked!)", "sync-task4", JobState.New, JobState.Active),

            StateChangeEvent(300, "CR: launch #1 starts on main thread", "cr-task1", JobState.New, JobState.Active),
            StateChangeEvent(350, "CR: launch #2 starts on main thread", "cr-task2", JobState.New, JobState.Active),
            StateChangeEvent(400, "CR: launch #3 starts on main thread", "cr-task3", JobState.New, JobState.Active),
            StateChangeEvent(450, "CR: launch #4 starts on main thread", "cr-task4", JobState.New, JobState.Active),

            StateChangeEvent(500, "CR: #1 enters withContext(IO) — switches to IO thread", "cr-task1-io", JobState.New, JobState.Active),
            NarrativeEvent(520, "CR: task 1 switched to IO thread via withContext — main thread still free"),

            StateChangeEvent(550, "CR: #1 suspends on main (running on IO)", "cr-task1", JobState.Active, JobState.Suspended),
            StateChangeEvent(600, "CR: #2 suspends (delay)", "cr-task2", JobState.Active, JobState.Suspended),
            StateChangeEvent(650, "CR: #3 suspends (delay)", "cr-task3", JobState.Active, JobState.Suspended),
            StateChangeEvent(700, "CR: #4 suspends (delay)", "cr-task4", JobState.Active, JobState.Suspended),

            NarrativeEvent(750, "Threads: 4 workers blocked | Coroutines: main free, only IO thread busy for task 1"),

            StateChangeEvent(900, "CR: withContext(IO) completed", "cr-task1-io", JobState.Active, JobState.Completed),
            StateChangeEvent(920, "CR: #1 resumes on main", "cr-task1", JobState.Suspended, JobState.Active),
            StateChangeEvent(950, "CR: #2 resumes", "cr-task2", JobState.Suspended, JobState.Active),
            StateChangeEvent(1000, "CR: #3 resumes", "cr-task3", JobState.Suspended, JobState.Active),
            StateChangeEvent(1050, "CR: #4 resumes", "cr-task4", JobState.Suspended, JobState.Active),

            StateChangeEvent(1100, "CR: #1 completed", "cr-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(1120, "CR: #2 completed", "cr-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1140, "CR: #3 completed", "cr-task3", JobState.Active, JobState.Completed),
            StateChangeEvent(1160, "CR: #4 completed", "cr-task4", JobState.Active, JobState.Completed),
            StateChangeEvent(1200, "CR: runBlocking completed", "cr-root", JobState.Active, JobState.Completed),

            StateChangeEvent(1100, "Threads: all tasks done", "sync-task1", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: task 2 done", "sync-task2", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: task 3 done", "sync-task3", JobState.Active, JobState.Completed),
            StateChangeEvent(1100, "Threads: task 4 done", "sync-task4", JobState.Active, JobState.Completed),
            StateChangeEvent(1150, "Threads: main completed", "sync-root", JobState.Active, JobState.Completed),

            NarrativeEvent(1400, "Same speed — coroutines used 2 threads vs 5, and only borrowed IO thread briefly!")
        )

        return EventTimeline(
            scenarioName = info.name,
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
