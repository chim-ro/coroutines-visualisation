package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class JobFactoryTrapScenario : Scenario {
    override val info = ScenarioInfo(
        id = "job-factory-trap",
        name = "Job() Factory Trap",
        description = "Creating a Job() manually is the classic 'never ends' trap — the Job doesn't auto-complete when its children finish, and job.join() will hang forever. You must call complete() explicitly. Compare with coroutineScope, which handles this automatically.",
        category = "Advanced"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: child finishes, but Job() stays Active until complete() ──
    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "manual-job",
            displayName = "Job() — manual",
            // Visualized as a CoroutineScope-shaped container; in reality
            // Job() creates a CompletableJob with no body of its own.
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child",
                    displayName = "launch(job)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Job() is the simplest factory for creating a Job manually. Unlike a Job built by coroutineScope, it does NOT auto-complete when its children finish."),
            StateChangeEvent(100, "Manual Job() becomes Active — the factory creates it Active", "manual-job", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch(job) starts — its Job is parented to the manual Job, not the surrounding scope", "child", JobState.New, JobState.Active),
            StateChangeEvent(800, "Child finishes its work", "child", JobState.Active, JobState.Completing),
            StateChangeEvent(900, "Child completed", "child", JobState.Completing, JobState.Completed),
            NarrativeEvent(1100, "Watch carefully: the child is Completed, but the manual Job() is STILL Active. This is the surprise."),
            NarrativeEvent(1600, "A scope-built Job (like coroutineScope's internal Job) auto-transitions Active → Completing → Completed once its children finish. Job() does not — it has no body, and the runtime can't know if more children are coming."),
            NarrativeEvent(2100, "To complete a manual Job, call job.complete(). It marks the Job as 'no more children' and lets it transition through the normal completion path."),
            StateChangeEvent(2400, "job.complete() called — Job enters Completing (children already done)", "manual-job", JobState.Active, JobState.Completing),
            StateChangeEvent(2500, "Manual Job completed", "manual-job", JobState.Completing, JobState.Completed),
            NarrativeEvent(2700, "Key insight: if you create a Job() manually, YOU are responsible for completing it. Otherwise it stays Active forever, and any job.join() on it will hang.")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    val job = Job()                       // creates a Job in Active state

    launch(job) {                         // child's Job is the manual one
        delay(200)
        println("Child done")
    }

    delay(500)
    println("Child has completed — but job is still Active!")

    job.complete()                        // marks "no more children coming"
    // The Job now transitions Active → Completing → Completed
    // (the child is already done, so it completes immediately)
}
            """.trimIndent()
        )
    }

    // ── Intermediate: job.join() hangs without complete() ────────────────
    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "manual-job",
            displayName = "Job() — manual",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-1",
                    displayName = "launch(job) #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-2",
                    displayName = "launch(job) #2",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "The 'never ends' trap: two children under a manual Job, then job.join() to wait for it. What happens if we forget complete()?"),
            StateChangeEvent(100, "Manual Job() becomes Active", "manual-job", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts", "child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "launch #2 starts", "child-2", JobState.New, JobState.Active),
            StateChangeEvent(900, "Child #1 finishes", "child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1000, "Child #1 completed", "child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(1200, "Child #2 finishes", "child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1300, "Child #2 completed", "child-2", JobState.Completing, JobState.Completed),
            NarrativeEvent(1500, "Both children are Completed. The caller now invokes job.join()..."),
            NarrativeEvent(2000, "...but the manual Job is STILL Active. job.join() suspends — waiting for a transition that will never come."),
            NarrativeEvent(2600, "In real code, the program hangs here. The classic 'never ends' bug from lesson 88."),
            NarrativeEvent(3200, "Recovery: call job.complete(). This marks the Job as 'no more children' and lets it transition. Since children are already done, it goes Active → Completing → Completed immediately."),
            StateChangeEvent(3500, "job.complete() called — Job enters Completing", "manual-job", JobState.Active, JobState.Completing),
            StateChangeEvent(3600, "Manual Job completed — join() unblocks", "manual-job", JobState.Completing, JobState.Completed),
            NarrativeEvent(3800, "Rule: if you create a Job() manually, ALWAYS pair the creation with a complete() call (typically in try/finally), or use a scope builder instead.")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    val job = Job()

    launch(job) {                         // #1
        delay(500)
        println("#1 done")
    }
    launch(job) {                         // #2
        delay(800)
        println("#2 done")
    }

    // Without job.complete(), this hangs FOREVER even after both children finish:
    //     job.join()
    //
    // Correct pattern:
    try {
        // ... launch more children here if needed ...
    } finally {
        job.complete()                    // signal "no more children"
    }
    job.join()                            // returns once children finish
}
            """.trimIndent()
        )
    }

    // ── Advanced: side-by-side — manual Job() vs coroutineScope ──────────
    private fun buildAdvancedTimeline(): EventTimeline {
        // Left tree: manual Job() approach — requires explicit complete()
        val tree = CoroutineNode(
            id = "manual-job",
            displayName = "Job() — manual",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "m-child-1",
                    displayName = "launch(job) #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "m-child-2",
                    displayName = "launch(job) #2",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        // Right tree: coroutineScope approach — auto-completes
        val secondTree = CoroutineNode(
            id = "auto-scope",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "a-child-1",
                    displayName = "launch #1",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "a-child-2",
                    displayName = "launch #2",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Same structure, two parents: LEFT is a manual Job(), RIGHT is coroutineScope. Watch which one auto-completes."),
            // Both parents become Active
            StateChangeEvent(100, "Manual Job() becomes Active", "manual-job", JobState.New, JobState.Active),
            StateChangeEvent(150, "coroutineScope becomes Active", "auto-scope", JobState.New, JobState.Active),
            // Children start on both sides
            StateChangeEvent(300, "L: child #1 starts", "m-child-1", JobState.New, JobState.Active),
            StateChangeEvent(350, "R: child #1 starts", "a-child-1", JobState.New, JobState.Active),
            StateChangeEvent(400, "L: child #2 starts", "m-child-2", JobState.New, JobState.Active),
            StateChangeEvent(450, "R: child #2 starts", "a-child-2", JobState.New, JobState.Active),
            // Children finish on both sides at the same times
            StateChangeEvent(900, "L: child #1 finishes", "m-child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(950, "R: child #1 finishes", "a-child-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1000, "L: child #1 completed", "m-child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(1050, "R: child #1 completed", "a-child-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(1200, "L: child #2 finishes", "m-child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1250, "R: child #2 finishes", "a-child-2", JobState.Active, JobState.Completing),
            StateChangeEvent(1300, "L: child #2 completed", "m-child-2", JobState.Completing, JobState.Completed),
            StateChangeEvent(1350, "R: child #2 completed", "a-child-2", JobState.Completing, JobState.Completed),
            NarrativeEvent(1500, "Both sides: all children Completed. Watch the parents next."),
            // RIGHT side auto-completes
            StateChangeEvent(1700, "R: coroutineScope enters Completing (auto — children done)", "auto-scope", JobState.Active, JobState.Completing),
            StateChangeEvent(1800, "R: coroutineScope Completed", "auto-scope", JobState.Completing, JobState.Completed),
            NarrativeEvent(2000, "Right side: coroutineScope auto-transitioned to Completed as soon as its children finished. This is the value of structured concurrency."),
            // LEFT side stays Active
            NarrativeEvent(2400, "Left side: manual Job() is still Active. It does not know if more children are coming. job.join() would hang here."),
            NarrativeEvent(3000, "Calling job.complete() on the manual Job — the explicit acknowledgment that we're done."),
            StateChangeEvent(3300, "L: manual Job enters Completing (complete() called)", "manual-job", JobState.Active, JobState.Completing),
            StateChangeEvent(3400, "L: manual Job Completed", "manual-job", JobState.Completing, JobState.Completed),
            NarrativeEvent(3600, "Takeaway: prefer coroutineScope (or supervisorScope) over manual Job(). The structured scope handles lifecycle automatically; a manual Job needs explicit complete() AND careful try/finally discipline.")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            secondTree = secondTree,
            events = events,
            kotlinCode = """
// LEFT — manual Job() (needs explicit complete())
suspend fun manualJob() = coroutineScope {
    val job = Job()
    launch(job) { delay(500); println("L #1 done") }
    launch(job) { delay(800); println("L #2 done") }

    try {
        // ... could launch more children here ...
    } finally {
        job.complete()                    // MANDATORY
    }
    job.join()
}

// RIGHT — coroutineScope (auto-managed)
suspend fun autoScope() = coroutineScope {
    launch { delay(500); println("R #1 done") }
    launch { delay(800); println("R #2 done") }
    // No complete() needed; coroutineScope returns when children finish.
}
            """.trimIndent()
        )
    }
}
