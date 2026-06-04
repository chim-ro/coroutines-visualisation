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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-inherits", "launch #1 (inherits)", BuilderType.Launch),
            node("child-named", "launch #2 (named)", BuilderType.Launch),
            node("child-broken", "launch #3", BuilderType.Launch)
        )

        // launch(Job()) is shown as a DETACHED tree — it is launched
        // lexically inside launch #3, but its Job is a standalone Job(),
        // so it is NOT structurally a child of any other coroutine.
        val orphanTree = node("orphan", "launch(Job()) — detached", BuilderType.Launch)

        val events = listOf(
            narrative(0, "CoroutineContext is a set of elements (Job, Dispatcher, CoroutineName, etc.) inherited by child coroutines. Children can override individual elements."),
            starts(100, "coroutineScope starts — its context includes Job, Dispatcher, and other elements inherited from the caller", "root"),
            starts(300, "launch #1 starts — inherits the full parent context (Job becomes child of parent's Job)", "child-inherits"),
            narrative(500, "Context inheritance: child context = parent context + child overrides. The child's Job is always new but is a child of the parent's Job."),
            starts(700, "launch #2 starts with CoroutineName(\"worker\") — overrides just the name element, inherits everything else", "child-named"),
            narrative(900, "The + operator merges context elements: launch(CoroutineName(\"worker\")) adds the name to the inherited context. Other elements (Job, Dispatcher) are unchanged."),
            starts(1100, "launch #3 starts normally as a child of the scope", "child-broken"),
            starts(1300, "launch(Job()) starts as a DETACHED coroutine — shown on the right because it is NOT a child of launch #3 (or any other coroutine in the tree)", "orphan"),
            narrative(1500, "⚠️ DANGER: launch(Job()) uses a standalone Job that is NOT a child of the parent's Job. The detached tree on the right shows this — the parent will NOT wait for this coroutine."),
            completing(1700, "launch #1 completes its work", "child-inherits"),
            completed(1900, "launch #1 fully completed", "child-inherits"),
            completing(2100, "launch #2 (\"worker\") completes — CoroutineName was accessible via coroutineContext[CoroutineName]", "child-named"),
            completed(2300, "launch #2 fully completed", "child-named"),
            completing(2500, "launch #3 completes — it does NOT wait for the orphaned child with standalone Job()", "child-broken"),
            completed(2700, "launch #3 fully completed without waiting for launch(Job())", "child-broken"),
            narrative(2900, "Notice: launch #3 completed while launch(Job()) is still running! The standalone Job() broke the parent-child relationship."),
            completing(3100, "Scope completes — it waited for children #1, #2, #3 but NOT for the orphaned coroutine", "root"),
            completed(3300, "Scope fully completed", "root"),
            narrative(3500, "The orphaned launch(Job()) is still running! It leaked — no parent will cancel it or wait for it. This is why you should never pass Job() to a coroutine builder."),
            completing(3700, "The orphaned coroutine eventually finishes on its own — but nobody was waiting for it", "orphan"),
            completed(3900, "Orphaned coroutine completed — leaked and unsupervised", "orphan"),
            narrative(4100, "Key insight: Never pass Job() or SupervisorJob() directly to a builder. The parallel antipattern launch(SupervisorJob()) ALSO breaks structured concurrency — it gives the new coroutine a standalone SupervisorJob as parent, so the surrounding scope doesn't wait for it OR cancel it. For supervisor semantics, use supervisorScope { } instead.")
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            suspend fun main() = coroutineScope {
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

                println("Scope done") // orphan may still be running!
            }
        """.trimIndent()

        return timeline(
            tree = tree,
            secondTree = orphanTree,
            events = events,
            kotlinCode = kotlinCode
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-inherits", "launch (inherits)", BuilderType.Launch)
        )

        val events = listOf(
            starts(0, "coroutineScope starts — its context includes a Job, a Dispatcher, and other elements inherited from the caller", "root"),
            narrative(200, "Every coroutine has a CoroutineContext — a set of elements like Job, Dispatcher, and CoroutineName. Child coroutines inherit their parent's context by default."),
            starts(400, "launch starts — it inherits the parent's context (Dispatcher, CoroutineName, etc.)", "child-inherits"),
            narrative(600, "The child's Job is a NEW Job, but it is registered as a child of the parent's Job. This parent-child Job relationship is the foundation of structured concurrency."),
            completing(1000, "Child coroutine finishes its work", "child-inherits"),
            completed(1200, "Child completed", "child-inherits"),
            completing(1400, "Scope completes — it waited for its child thanks to context inheritance", "root"),
            completed(1600, "Scope fully completed", "root")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    // Child inherits parent's context (Dispatcher, etc.)
    // Its Job becomes a child of the scope's Job
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
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-inherits", "launch #1 (inherits)", BuilderType.Launch),
            node("child-named", "launch #2 (CoroutineName)", BuilderType.Launch),
            node("child-dispatched", "launch #3 (Dispatchers.Default)", BuilderType.Launch),
            node("child-broken", "launch #4", BuilderType.Launch)
        )

        // launch(Job()) is shown as a DETACHED tree — it is launched
        // lexically inside launch #4, but its Job is a standalone Job(),
        // so it is NOT structurally a child of any other coroutine.
        val orphanTree = node("orphan", "launch(Job()) — detached", BuilderType.Launch)

        val events = listOf(
            starts(0, "coroutineScope starts — its context: Job + inherited dispatcher + no CoroutineName", "root"),
            narrative(200, "CoroutineContext is like a map of Element keys to values. Elements include: Job, ContinuationInterceptor (Dispatcher), CoroutineName, CoroutineExceptionHandler, and more."),
            starts(400, "launch #1 starts — inherits the full parent context; its Job becomes a child of root's Job", "child-inherits"),
            starts(600, "launch #2 starts with CoroutineName(\"worker\") — the + operator merges it into the inherited context", "child-named"),
            narrative(800, "Context merging with +: parentContext + CoroutineName(\"worker\") replaces only the CoroutineName element. Job and Dispatcher are still inherited. Formula: child context = parent context + child overrides."),
            starts(1000, "launch #3 starts with Dispatchers.Default — overrides the Dispatcher element, inherits Job and everything else", "child-dispatched"),
            narrative(1200, "Dispatchers.Default runs coroutines on a shared thread pool. This child will execute on a different thread than its parent, but its Job is still a child of root's Job — structured concurrency is preserved."),
            starts(1400, "launch #4 starts normally as a child of the scope", "child-broken"),
            starts(1600, "launch(Job()) starts as a DETACHED coroutine (shown on the right) — its Job replaces the inherited one, so it is NOT a child of launch #4", "orphan"),
            narrative(1800, "DANGER: launch(Job()) uses context + Job(), which replaces the inherited Job element. The new coroutine's Job is a child of the standalone Job() — NOT of launch #4's Job. The detached tree on the right reflects this — structured concurrency is broken."),
            // Normal children complete
            completing(2200, "launch #1 (inherits) completes its work", "child-inherits"),
            completed(2400, "launch #1 fully completed", "child-inherits"),
            completing(2600, "launch #2 (CoroutineName) completes — name was accessible via coroutineContext[CoroutineName]", "child-named"),
            completed(2800, "launch #2 fully completed", "child-named"),
            completing(3000, "launch #3 (Dispatchers.Default) completes — ran on a background thread but was still a proper child", "child-dispatched"),
            completed(3200, "launch #3 fully completed", "child-dispatched"),
            // Broken child completes WITHOUT waiting for orphan
            completing(3400, "launch #4 completes — it does NOT wait for the orphaned launch(Job()) child", "child-broken"),
            completed(3600, "launch #4 fully completed without waiting for launch(Job())", "child-broken"),
            // Root completes
            completing(3800, "Scope completes — it waited for children #1-#4 but NOT for the orphaned coroutine", "root"),
            completed(4000, "Scope fully completed", "root"),
            // Orphan finishes alone
            narrative(4200, "The orphaned launch(Job()) is still running! It leaked — no parent will cancel it or wait for it."),
            completing(4400, "The orphaned coroutine eventually finishes on its own — but nobody was waiting for it", "orphan"),
            completed(4600, "Orphaned coroutine completed — leaked and unsupervised", "orphan"),
            narrative(4800, "Key takeaway: The + operator merges context elements. CoroutineName and Dispatchers are safe overrides. Job() and SupervisorJob() are BOTH antipatterns when passed to a builder — they replace the inherited Job, detaching the new coroutine from structured concurrency. For supervisor semantics, use supervisorScope { } (which uses a SupervisorJob internally while preserving the parent-child link).")
        )

        return timeline(
            tree = tree,
            secondTree = orphanTree,
            events = events,
            kotlinCode = """
import kotlinx.coroutines.*

suspend fun main() = coroutineScope { // inherits caller's context
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

    println("Scope done") // orphan may still be running!
}
            """.trimIndent()
        )
    }
}
