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
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child",
                    displayName = "launch (cooperative)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "A cooperative coroutine checks for cancellation"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch starts — doing CPU-bound work", "child", JobState.New, JobState.Active),
            NarrativeEvent(600, "Child uses delay() / ensureActive() — these are cancellation points"),
            NarrativeEvent(900, "Parent cancels the child..."),
            CancellationEvent(1000, "Parent sends cancellation signal", "root", "child"),
            NarrativeEvent(1200, "Child reaches next cancellation point (delay/ensureActive)..."),
            StateChangeEvent(1400, "Child detects cancellation — enters Cancelling", "child", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1600, "Child is Cancelled", "child", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1800, "Parent scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(1900, "Parent scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2000, "Cooperative cancellation: coroutine checks for cancellation at suspension points")
        )

        return EventTimeline(
            scenarioName = info.name,
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
        val tree1 = CoroutineNode(
            id = "coop-root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "coop-child",
                    displayName = "launch (cooperative)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val tree2 = CoroutineNode(
            id = "noncoop-root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "noncoop-child",
                    displayName = "launch (non-cooperative)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Left: cooperative child (checks isActive) | Right: non-cooperative (ignores cancellation)"),
            // Both scopes start
            StateChangeEvent(100, "Left scope starts", "coop-root", JobState.New, JobState.Active),
            StateChangeEvent(150, "Right scope starts", "noncoop-root", JobState.New, JobState.Active),
            StateChangeEvent(300, "Cooperative child starts", "coop-child", JobState.New, JobState.Active),
            StateChangeEvent(350, "Non-cooperative child starts", "noncoop-child", JobState.New, JobState.Active),
            NarrativeEvent(700, "Both parents send cancellation signal..."),
            CancellationEvent(800, "Left: cancellation signal sent", "coop-root", "coop-child"),
            CancellationEvent(850, "Right: cancellation signal sent", "noncoop-root", "noncoop-child"),
            StateChangeEvent(870, "Right child's Job flips to Cancelling immediately (but body keeps running)", "noncoop-child", JobState.Active, JobState.Cancelling),
            NarrativeEvent(1000, "Cooperative child checks isActive — detects cancellation!"),
            StateChangeEvent(1200, "Cooperative child enters Cancelling", "coop-child", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1400, "Cooperative child cancelled", "coop-child", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1500, "Non-cooperative child ignores the signal — body still running while Job is Cancelling"),
            NarrativeEvent(1800, "Non-cooperative child still doing work..."),
            NarrativeEvent(2100, "Non-cooperative child still running — no cancellation check!"),
            NarrativeEvent(2300, "Non-cooperative body finishes — but Job was Cancelling, so final state is Cancelled (not Completed)"),
            StateChangeEvent(2400, "Non-cooperative child Cancelled (cancel() wins over natural completion)", "noncoop-child", JobState.Cancelling, JobState.Cancelled),
            // Both scopes finish
            StateChangeEvent(2600, "Left scope completing", "coop-root", JobState.Active, JobState.Completing),
            StateChangeEvent(2650, "Right scope completing", "noncoop-root", JobState.Active, JobState.Completing),
            StateChangeEvent(2700, "Left scope completed", "coop-root", JobState.Completing, JobState.Completed),
            StateChangeEvent(2750, "Right scope completed", "noncoop-root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2900, "Both children ended in Cancelled — the difference is whether the body cooperated (left stopped early) or not (right ran to the end but still ends Cancelled)")
        )

        return EventTimeline(
            scenarioName = info.name,
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
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch #1 (ensureActive)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "launch #2 (isActive)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-3",
                    displayName = "launch #3 (non-cooperative)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Three strategies: ensureActive() (throws), isActive (graceful), non-cooperative (ignores)"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 (ensureActive) starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 (isActive) starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #3 (non-cooperative) starts", "child-3", JobState.New, JobState.Active),
            NarrativeEvent(800, "Parent cancels all children..."),
            CancellationEvent(900, "Cancellation signal to #1", "root", "child-1"),
            CancellationEvent(950, "Cancellation signal to #2", "root", "child-2"),
            CancellationEvent(1000, "Cancellation signal to #3", "root", "child-3"),
            StateChangeEvent(1020, "#3's Job flips to Cancelling immediately (body unaware, keeps running)", "child-3", JobState.Active, JobState.Cancelling),
            NarrativeEvent(1100, "#1 calls ensureActive() — throws CancellationException immediately!"),
            StateChangeEvent(1200, "#1 enters Cancelling (ensureActive threw)", "child-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1300, "#1 cancelled", "child-1", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1400, "#2 checks isActive — false! Exits loop gracefully, runs cleanup"),
            StateChangeEvent(1600, "#2 enters Cancelling (graceful exit)", "child-2", JobState.Active, JobState.Cancelling),
            NarrativeEvent(1700, "#2 performs cleanup before finishing cancellation..."),
            StateChangeEvent(1900, "#2 cancelled after cleanup", "child-2", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2000, "#3 has no cancellation checks — body keeps running, but Job is Cancelling"),
            NarrativeEvent(2300, "#3 still ignoring cancellation signal..."),
            NarrativeEvent(2500, "#3 body finishes — Job was Cancelling, so final state is Cancelled (not Completed)"),
            StateChangeEvent(2600, "#3 Cancelled (cancel() wins over natural completion)", "child-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2800, "Parent scope completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2900, "Parent scope completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3000, "ensureActive(): fastest, throws immediately | isActive: graceful, allows cleanup | non-cooperative: still ends Cancelled, but only after body runs to completion"),
            NarrativeEvent(3100, "A fourth option: yield() — like ensureActive() it throws on cancellation, AND it gives other coroutines a chance to run by rotating the dispatcher queue. Prefer yield() in long CPU-bound loops where you also want cooperative scheduling.")
        )

        return EventTimeline(
            scenarioName = info.name,
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
