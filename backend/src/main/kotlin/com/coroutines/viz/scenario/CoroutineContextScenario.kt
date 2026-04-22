package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class CoroutineContextScenario : Scenario {
    override val info = ScenarioInfo(
        id = "coroutine-context",
        name = "CoroutineContext Inheritance",
        description = "Shows how coroutine context elements are inherited from parent to child, how to override elements, and the danger of passing Job() which breaks structured concurrency.",
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
                    id = "child-inherits",
                    displayName = "launch #1 (inherits)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-named",
                    displayName = "launch #2 (named)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-broken",
                    displayName = "launch #3",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "orphan",
                            displayName = "launch(Job())",
                            builder = BuilderType.Launch,
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
                description = "CoroutineContext is a set of elements (Job, Dispatcher, CoroutineName, etc.) inherited by child coroutines. Children can override individual elements."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "runBlocking starts — its context includes Job, Dispatcher, and other default elements",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 300,
                description = "launch #1 starts — inherits the full parent context (Job becomes child of parent's Job)",
                nodeId = "child-inherits",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 500,
                description = "Context inheritance: child context = parent context + child overrides. The child's Job is always new but is a child of the parent's Job."
            ),
            StateChangeEvent(
                delayMs = 700,
                description = "launch #2 starts with CoroutineName(\"worker\") — overrides just the name element, inherits everything else",
                nodeId = "child-named",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 900,
                description = "The + operator merges context elements: launch(CoroutineName(\"worker\")) adds the name to the inherited context. Other elements (Job, Dispatcher) are unchanged."
            ),
            StateChangeEvent(
                delayMs = 1100,
                description = "launch #3 starts normally as a child of runBlocking",
                nodeId = "child-broken",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 1300,
                description = "Nested launch(Job()) starts — passing a standalone Job() breaks the parent-child link!",
                nodeId = "orphan",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1500,
                description = "⚠️ DANGER: launch(Job()) uses a standalone Job that is NOT a child of the parent's Job. This breaks structured concurrency — the parent will NOT wait for this coroutine."
            ),
            StateChangeEvent(
                delayMs = 1700,
                description = "launch #1 completes its work",
                nodeId = "child-inherits",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 1900,
                description = "launch #1 fully completed",
                nodeId = "child-inherits",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2100,
                description = "launch #2 (\"worker\") completes — CoroutineName was accessible via coroutineContext[CoroutineName]",
                nodeId = "child-named",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2300,
                description = "launch #2 fully completed",
                nodeId = "child-named",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2500,
                description = "launch #3 completes — it does NOT wait for the orphaned child with standalone Job()",
                nodeId = "child-broken",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2700,
                description = "launch #3 fully completed without waiting for launch(Job())",
                nodeId = "child-broken",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 2900,
                description = "Notice: launch #3 completed while launch(Job()) is still running! The standalone Job() broke the parent-child relationship."
            ),
            StateChangeEvent(
                delayMs = 3100,
                description = "runBlocking completes — it waited for children #1, #2, #3 but NOT for the orphaned coroutine",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3300,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 3500,
                description = "The orphaned launch(Job()) is still running! It leaked — no parent will cancel it or wait for it. This is why you should never pass Job() to a coroutine builder."
            ),
            StateChangeEvent(
                delayMs = 3700,
                description = "The orphaned coroutine eventually finishes on its own — but nobody was waiting for it",
                nodeId = "orphan",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3900,
                description = "Orphaned coroutine completed — leaked and unsupervised",
                nodeId = "orphan",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 4100,
                description = "Key insight: Never pass Job() or SupervisorJob() directly to a builder. To use SupervisorJob, use supervisorScope { } instead, which properly maintains the parent-child hierarchy."
            )
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            fun main() = runBlocking {
                // launch #1 — inherits full parent context
                launch {
                    println("Name: ${'$'}{coroutineContext[CoroutineName]}") // null
                    delay(500L)
                }

                // launch #2 — overrides CoroutineName element
                launch(CoroutineName("worker")) {
                    val name = coroutineContext[CoroutineName]
                    println("Name: ${'$'}name") // CoroutineName(worker)
                    delay(500L)
                }

                // launch #3 — contains a dangerous nested launch
                launch {
                    // ⚠️ DANGER: Job() breaks structured concurrency!
                    launch(Job()) {
                        delay(5000L) // runs independently
                        println("Orphan done") // parent won't wait
                    }
                    delay(200L)
                } // completes without waiting for launch(Job())

                println("runBlocking done") // orphan may still be running!
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
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> buildTimeline()
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-inherits",
                    displayName = "launch (inherits)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                )
            )
        )

        val events = listOf(
            StateChangeEvent(
                delayMs = 0,
                description = "runBlocking starts — its context includes a Job, a Dispatcher, and other default elements",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 200,
                description = "Every coroutine has a CoroutineContext — a set of elements like Job, Dispatcher, and CoroutineName. Child coroutines inherit their parent's context by default."
            ),
            StateChangeEvent(
                delayMs = 400,
                description = "launch starts — it inherits the parent's context (Dispatcher, CoroutineName, etc.)",
                nodeId = "child-inherits",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 600,
                description = "The child's Job is a NEW Job, but it is registered as a child of the parent's Job. This parent-child Job relationship is the foundation of structured concurrency."
            ),
            StateChangeEvent(
                delayMs = 1000,
                description = "Child coroutine finishes its work",
                nodeId = "child-inherits",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 1200,
                description = "Child completed",
                nodeId = "child-inherits",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 1400,
                description = "runBlocking completes — it waited for its child thanks to context inheritance",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 1600,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            )
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
import kotlinx.coroutines.*

fun main() = runBlocking {
    // Child inherits parent's context (Dispatcher, etc.)
    // Its Job becomes a child of runBlocking's Job
    launch {
        println("Child context: ${'$'}coroutineContext")
        delay(100L)
    }
    println("Parent waiting for child...")
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "child-inherits",
                    displayName = "launch #1 (inherits)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-named",
                    displayName = "launch #2 (CoroutineName)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-dispatched",
                    displayName = "launch #3 (Dispatchers.Default)",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New
                ),
                CoroutineNode(
                    id = "child-broken",
                    displayName = "launch #4",
                    builder = BuilderType.Launch,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "orphan",
                            displayName = "launch(Job())",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            StateChangeEvent(
                delayMs = 0,
                description = "runBlocking starts — its context: Job + BlockingEventLoop dispatcher + no CoroutineName",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 200,
                description = "CoroutineContext is like a map of Element keys to values. Elements include: Job, ContinuationInterceptor (Dispatcher), CoroutineName, CoroutineExceptionHandler, and more."
            ),
            StateChangeEvent(
                delayMs = 400,
                description = "launch #1 starts — inherits the full parent context; its Job becomes a child of root's Job",
                nodeId = "child-inherits",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 600,
                description = "launch #2 starts with CoroutineName(\"worker\") — the + operator merges it into the inherited context",
                nodeId = "child-named",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 800,
                description = "Context merging with +: parentContext + CoroutineName(\"worker\") replaces only the CoroutineName element. Job and Dispatcher are still inherited. Formula: child context = parent context + child overrides."
            ),
            StateChangeEvent(
                delayMs = 1000,
                description = "launch #3 starts with Dispatchers.Default — overrides the Dispatcher element, inherits Job and everything else",
                nodeId = "child-dispatched",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1200,
                description = "Dispatchers.Default runs coroutines on a shared thread pool. This child will execute on a different thread than its parent, but its Job is still a child of root's Job — structured concurrency is preserved."
            ),
            StateChangeEvent(
                delayMs = 1400,
                description = "launch #4 starts normally as a child of runBlocking",
                nodeId = "child-broken",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 1600,
                description = "Nested launch(Job()) starts — passing a standalone Job() REPLACES the inherited Job, breaking the parent-child link!",
                nodeId = "orphan",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1800,
                description = "DANGER: launch(Job()) uses context + Job(), which replaces the inherited Job element. The new coroutine's Job is a child of the standalone Job() — NOT of launch #4's Job. Structured concurrency is broken."
            ),
            // Normal children complete
            StateChangeEvent(
                delayMs = 2200,
                description = "launch #1 (inherits) completes its work",
                nodeId = "child-inherits",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2400,
                description = "launch #1 fully completed",
                nodeId = "child-inherits",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2600,
                description = "launch #2 (CoroutineName) completes — name was accessible via coroutineContext[CoroutineName]",
                nodeId = "child-named",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2800,
                description = "launch #2 fully completed",
                nodeId = "child-named",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 3000,
                description = "launch #3 (Dispatchers.Default) completes — ran on a background thread but was still a proper child",
                nodeId = "child-dispatched",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3200,
                description = "launch #3 fully completed",
                nodeId = "child-dispatched",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            // Broken child completes WITHOUT waiting for orphan
            StateChangeEvent(
                delayMs = 3400,
                description = "launch #4 completes — it does NOT wait for the orphaned launch(Job()) child",
                nodeId = "child-broken",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3600,
                description = "launch #4 fully completed without waiting for launch(Job())",
                nodeId = "child-broken",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            // Root completes
            StateChangeEvent(
                delayMs = 3800,
                description = "runBlocking completes — it waited for children #1-#4 but NOT for the orphaned coroutine",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 4000,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            // Orphan finishes alone
            NarrativeEvent(
                delayMs = 4200,
                description = "The orphaned launch(Job()) is still running! It leaked — no parent will cancel it or wait for it."
            ),
            StateChangeEvent(
                delayMs = 4400,
                description = "The orphaned coroutine eventually finishes on its own — but nobody was waiting for it",
                nodeId = "orphan",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 4600,
                description = "Orphaned coroutine completed — leaked and unsupervised",
                nodeId = "orphan",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 4800,
                description = "Key takeaway: The + operator merges context elements — CoroutineName and Dispatchers are safe overrides. But Job() replaces the parent-child link, breaking structured concurrency. Use supervisorScope { } instead of SupervisorJob()."
            )
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
import kotlinx.coroutines.*

fun main() = runBlocking { // context: Job + BlockingEventLoop
    // #1 — inherits full parent context
    launch {
        println("Dispatcher: ${'$'}{coroutineContext[ContinuationInterceptor]}")
        delay(500L)
    }

    // #2 — adds CoroutineName via + operator
    launch(CoroutineName("worker")) {
        val name = coroutineContext[CoroutineName]
        println("Name: ${'$'}name") // CoroutineName(worker)
        delay(500L)
    }

    // #3 — overrides Dispatcher via + operator
    launch(Dispatchers.Default) {
        println("Thread: ${'$'}{Thread.currentThread().name}") // DefaultDispatcher
        delay(500L)
    }

    // #4 — contains a dangerous nested launch
    launch {
        // DANGER: Job() replaces the inherited Job element!
        // context + Job() = parent context with NEW standalone Job
        launch(Job()) {
            delay(5000L) // runs independently — leaked!
            println("Orphan done") // parent won't wait
        }
        delay(200L)
    } // completes without waiting for launch(Job())

    println("runBlocking done") // orphan may still be running!
}
            """.trimIndent()
        )
    }
}
