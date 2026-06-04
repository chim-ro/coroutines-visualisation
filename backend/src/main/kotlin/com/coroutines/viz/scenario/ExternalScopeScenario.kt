package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ExternalScopeScenario : Scenario {
    override val info = ScenarioInfo(
        id = "external-scope",
        name = "External Scope (Fire-and-Forget)",
        description = "The right way to detach work from a short-lived scope: launch into a long-lived 'external' scope (typically CoroutineScope(SupervisorJob()) held by an application or service). The handler can return immediately, the background work outlives it, AND the work is still structured — one externalScope.cancel() at shutdown stops everything cleanly.",
        category = "Advanced"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: pattern intro — handler completes, external child continues ──
    private fun buildBeginnerTimeline(): EventTimeline {
        val handler = node("h-scope", "coroutineScope (handler)", BuilderType.CoroutineScope,
            node("h-main", "launch (main work)", BuilderType.Launch)
        )

        val external = supervisorNode("x-scope", "externalScope (CoroutineScope(SupervisorJob()))", BuilderType.CoroutineScope,
            node("x-bg", "launch (background work)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two scopes: LEFT is the request handler (short-lived, coroutineScope), RIGHT is the application's externalScope (long-lived). The handler will kick off background work into the external scope."),
            starts(100, "handler-scope Active", "h-scope"),
            starts(150, "externalScope Active (created at app startup, runs forever)", "x-scope"),
            starts(300, "Main work starts inside handler", "h-main"),
            completing(700, "Main work finishes", "h-main"),
            completed(800, "Main work Completed", "h-main"),
            narrative(1000, "Handler body calls externalScope.launch { ... } — the new coroutine appears in the EXTERNAL tree (parented to externalScope), not in the handler tree."),
            starts(1200, "Background work starts (parented to externalScope, not handler)", "x-bg"),
            narrative(1400, "Handler body returns. Since the background launch is NOT a child of the handler, the handler can complete immediately."),
            completing(1600, "handler-scope Completing (its only direct child, h-main, is done)", "h-scope"),
            completed(1700, "handler-scope Completed — handler returns to caller", "h-scope"),
            narrative(1900, "Background work keeps running in externalScope — it doesn't care that the handler is gone."),
            completing(2300, "Background work finishes", "x-bg"),
            completed(2400, "Background work Completed", "x-bg"),
            narrative(2600, "Pattern: pass externalScope as a constructor dependency. From inside a short-lived scope, use externalScope.launch { } for fire-and-forget work that should outlive the current operation but still be cancellable at app shutdown.")
        )

        return timeline(
            tree = handler,
            secondTree = external,
            events = events,
            kotlinCode = """
class MyService(private val externalScope: CoroutineScope) {

    suspend fun handleRequest() = coroutineScope {
        // Main work — handler waits for this
        launch {
            delay(400)
            println("main work done")
        }

        // Fire-and-forget — launched into externalScope, NOT this scope
        externalScope.launch {
            delay(1000)
            println("background work done")
        }
        // Handler returns here, background work keeps running.
    }
}

// Wiring:
val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
val service = MyService(externalScope)
            """.trimIndent()
        )
    }

    // ── Intermediate: same pattern + app shutdown via externalScope.cancel() ──
    private fun buildIntermediateTimeline(): EventTimeline {
        val handler = node("h-scope", "coroutineScope (handler)", BuilderType.CoroutineScope,
            node("h-main", "launch (main work)", BuilderType.Launch)
        )

        val external = supervisorNode("x-scope", "externalScope (CoroutineScope(SupervisorJob()))", BuilderType.CoroutineScope,
            node("x-bg", "launch (long background work)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Same pattern, but now the background work is SLOW — and the app shuts down before it finishes. Watch how externalScope.cancel() cancels it cleanly."),
            starts(100, "handler-scope Active", "h-scope"),
            starts(150, "externalScope Active", "x-scope"),
            starts(300, "Main work starts", "h-main"),
            completing(700, "Main work Completing", "h-main"),
            completed(800, "Main work Completed", "h-main"),
            narrative(1000, "Handler launches a long background task into externalScope."),
            starts(1200, "Background work Active (will need ~3 seconds)", "x-bg"),
            completing(1400, "handler-scope Completing", "h-scope"),
            completed(1500, "handler-scope Completed — handler returns immediately, doesn't wait for background", "h-scope"),
            narrative(1800, "Background work continues — it's only ~600ms into a 3s task."),
            narrative(2400, "App shutdown! The application calls externalScope.cancel() during teardown — one call cancels EVERYTHING in the external scope cleanly."),
            cancellation(2600, "externalScope.cancel() — propagates to background work", "x-scope", "x-bg"),
            cancelling(2700, "externalScope → Cancelling", "x-scope"),
            cancelling(2800, "Background work → Cancelling (cooperative cancellation)", "x-bg"),
            cancelled(3000, "Background work Cancelled (cleanup ran, terminated cleanly)", "x-bg"),
            cancelled(3200, "externalScope Cancelled — app shutdown complete", "x-scope"),
            narrative(3400, "This is the value of externalScope: structured shutdown. Compare with launch(Job()) — orphans created that way are unreachable; you can't cancel them at app shutdown. They leak until they naturally finish (or the JVM dies).")
        )

        return timeline(
            tree = handler,
            secondTree = external,
            events = events,
            kotlinCode = """
class App {
    // App-level scope — survives across all requests
    val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun shutdown() {
        externalScope.cancel()    // cancels all background work cleanly
    }
}

class MyService(private val externalScope: CoroutineScope) {
    suspend fun handleRequest() = coroutineScope {
        launch { delay(400); println("main done") }

        externalScope.launch {
            delay(3000)           // long background task
            println("bg done")    // never reached if app shuts down first
        }
    }
}
            """.trimIndent()
        )
    }

    // ── Advanced: side-by-side — orphan (launch(Job())) vs externalScope.launch ──
    private fun buildAdvancedTimeline(): EventTimeline {
        // LEFT — the orphan (no scope can reach it)
        val orphanWorld = node("orphan", "launch(Job()) — orphan", BuilderType.Launch)

        // RIGHT — externalScope holds the bg task
        val externalWorld = supervisorNode("x-scope", "externalScope", BuilderType.CoroutineScope,
            node("x-bg", "launch (background work)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two ways to detach: LEFT uses launch(Job()) — creates an unreachable orphan. RIGHT uses externalScope.launch — properly structured. Both look the same while running. The difference shows at app shutdown."),
            // Setup
            starts(100, "RIGHT: externalScope Active", "x-scope"),
            narrative(200, "LEFT: a handler ran launch(Job()) { ... } and then returned. The orphan now exists with a standalone Job — no parent scope tracks it."),
            narrative(250, "RIGHT: a handler ran externalScope.launch { ... } and then returned. The bg task is parented to externalScope."),
            starts(400, "LEFT: orphan Active (running, but no scope owns it)", "orphan"),
            starts(450, "RIGHT: background work Active", "x-bg"),
            narrative(800, "Both background tasks are doing the same work. From a casual look at running coroutines, you can't tell them apart."),
            narrative(1400, "Suddenly: app shutdown! We want to stop all background work cleanly."),
            // RIGHT: clean shutdown
            narrative(1700, "RIGHT: app code calls externalScope.cancel() — one call, propagates to all children."),
            cancellation(1900, "RIGHT: externalScope cancels its bg child", "x-scope", "x-bg"),
            cancelling(2000, "RIGHT: externalScope → Cancelling", "x-scope"),
            cancelling(2100, "RIGHT: bg work → Cancelling", "x-bg"),
            cancelled(2300, "RIGHT: bg work Cancelled — cleanup ran", "x-bg"),
            cancelled(2500, "RIGHT: externalScope Cancelled — graceful shutdown complete", "x-scope"),
            // LEFT: nothing can cancel the orphan
            narrative(2700, "LEFT: app code wants to cancel the orphan too — but there's no scope to call .cancel() on. The orphan is unreachable. It keeps running."),
            narrative(3300, "LEFT: orphan is still alive, doing work nobody wanted anymore. It can hold resources, write to closed files, log misleading errors..."),
            completing(3700, "LEFT: orphan eventually finishes on its own (much later than we wanted)", "orphan"),
            completed(3800, "LEFT: orphan Completed (uncontrollably late)", "orphan"),
            narrative(4000, "Rule of thumb: never use launch(Job()) for fire-and-forget. Inject an externalScope (CoroutineScope(SupervisorJob() + dispatcher)) into anything that needs to detach. One cancel() at shutdown cleans everything up.")
        )

        return timeline(
            tree = orphanWorld,
            secondTree = externalWorld,
            events = events,
            kotlinCode = """
// LEFT — WRONG: launch(Job()) creates an unreachable orphan
suspend fun handleWrong() = coroutineScope {
    launch(Job()) {                       // ⚠️ no parent scope
        delay(2000)
        println("orphan done")
    }
    // handler returns, orphan keeps running forever
    // — no way to cancel it at app shutdown
}


// RIGHT — externalScope.launch keeps the work structured
class Service(private val externalScope: CoroutineScope) {
    suspend fun handleRight() = coroutineScope {
        externalScope.launch {            // ✅ parented to externalScope
            delay(2000)
            println("bg done")
        }
    }
}

class App {
    val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun shutdown() = externalScope.cancel()   // one call, everything cleaned up
}
            """.trimIndent()
        )
    }
}
