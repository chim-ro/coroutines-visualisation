package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class AsyncAwaitExceptionScenario : Scenario {
    override val info = ScenarioInfo(
        id = "async-await-exception",
        name = "Async/Await Exception",
        description = "Exception inside async is deferred — only surfaces when .await() is called.",
        category = "Exceptions"
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
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "async-1",
                    displayName = "async (fails)",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope starts with one async child"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "async starts", "async-1", JobState.New, JobState.Active),
            NarrativeEvent(600, "async encounters an error internally..."),
            StateChangeEvent(800, "async fails — enters Cancelling", "async-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(900, "async is Cancelled", "async-1", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1000, "Exception is stored in the Deferred — NOT propagated yet!"),
            NarrativeEvent(1300, "Parent calls .await() on the Deferred..."),
            ExceptionEvent(1500, "Exception surfaces when .await() is called", "async-1", "root", "RuntimeException: async failed"),
            StateChangeEvent(1700, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1900, "Parent scope cancelled", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2000, "async exceptions are deferred — they only propagate when .await() is called")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
supervisorScope {
    val deferred = async {
        delay(300)
        throw RuntimeException("async failed")
    }
    // Under supervisorScope, the exception is stored in
    // the Deferred and not propagated to the parent.
    delay(1000)
    deferred.await()  // NOW the exception is thrown
}
            """.trimIndent()
        )
    }

    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "async-1",
                    displayName = "async (fails)",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "launch-1",
                    displayName = "launch (sibling)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope starts with async + launch sibling"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "async starts", "async-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch sibling starts", "launch-1", JobState.New, JobState.Active),
            NarrativeEvent(700, "async encounters an error internally..."),
            StateChangeEvent(900, "async fails — enters Cancelling", "async-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1000, "async is Cancelled", "async-1", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1100, "Exception stored in Deferred — sibling keeps running!"),
            NarrativeEvent(1400, "Sibling is still working normally..."),
            NarrativeEvent(1700, "Parent calls .await() — exception propagates NOW"),
            ExceptionEvent(1800, "Exception surfaces from .await()", "async-1", "root", "RuntimeException: async failed"),
            StateChangeEvent(1900, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            CancellationEvent(2000, "Parent cancels launch sibling", "root", "launch-1"),
            StateChangeEvent(2100, "launch sibling enters Cancelling", "launch-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2200, "launch sibling cancelled", "launch-1", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2400, "Parent scope cancelled", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2500, "Sibling was fine until .await() triggered propagation — that's the deferred exception pattern")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
supervisorScope {
    val deferred = async {
        delay(300)
        throw RuntimeException("async failed")
    }

    launch {  // sibling keeps running
        delay(5000)
        println("Sibling done")
    }

    delay(1000)
    deferred.await()  // Exception propagates here, cancels sibling
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "supervisorScope",
            builder = BuilderType.SupervisorScope,
            jobType = JobType.SupervisorJob,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "async-1",
                    displayName = "async #1 (fails)",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "async-2",
                    displayName = "async #2",
                    builder = BuilderType.Async,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "launch-3",
                    displayName = "launch #3",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "supervisorScope with async#1(fails), async#2, launch#3"),
            StateChangeEvent(100, "Scope becomes Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "async #1 starts", "async-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "async #2 starts", "async-2", JobState.New, JobState.Active),
            StateChangeEvent(500, "launch #3 starts", "launch-3", JobState.New, JobState.Active),
            NarrativeEvent(800, "async #1 fails at t=800 — exception stored silently"),
            StateChangeEvent(900, "async #1 fails — enters Cancelling", "async-1", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1000, "async #1 is Cancelled", "async-1", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1100, "Exception stored in Deferred #1 — async#2 and launch#3 continue working"),
            NarrativeEvent(1400, "launch #3 and async #2 still running normally..."),
            NarrativeEvent(1700, "Parent calls await() on async #1 at t=1800"),
            ExceptionEvent(1800, "Exception surfaces from async #1's .await()", "async-1", "root", "RuntimeException: async #1 failed"),
            StateChangeEvent(1900, "Parent scope enters Cancelling", "root", JobState.Active, JobState.Cancelling),
            CancellationEvent(2000, "Parent cancels async #2", "root", "async-2"),
            StateChangeEvent(2100, "async #2 enters Cancelling", "async-2", JobState.Active, JobState.Cancelling),
            CancellationEvent(2200, "Parent cancels launch #3", "root", "launch-3"),
            StateChangeEvent(2300, "launch #3 enters Cancelling", "launch-3", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2500, "async #2 cancelled", "async-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2600, "launch #3 cancelled", "launch-3", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2800, "Parent scope cancelled", "root", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2900, "async #1 failed at t=800 but siblings ran until await() at t=1800 — 1000ms of wasted work!")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
supervisorScope {
    val d1 = async {
        delay(800)
        throw RuntimeException("async #1 failed")
    }

    val d2 = async {
        delay(5000)
        "result from async #2"
    }

    launch {  // #3
        delay(5000)
        println("launch #3 done")
    }

    // Siblings run for 1000ms after async#1 fails
    delay(1800)
    d1.await()  // Exception propagates — cancels d2 and launch#3
    d2.await()  // Never reached
}
            """.trimIndent()
        )
    }
}
