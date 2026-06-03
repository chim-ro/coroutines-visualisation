package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class CoroutineExceptionHandlerScenario : Scenario {
    override val info = ScenarioInfo(
        id = "coroutine-exception-handler",
        name = "CoroutineExceptionHandler",
        description = "A CoroutineExceptionHandler (CEH) is a context element that catches uncaught exceptions at the root of a coroutine tree. It catches launch failures (under SupervisorJob), but NOT async failures (those live in the Deferred). Learn where to install it and what it actually sees.",
        category = "Exceptions"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: supervisorScope + CEH, one failing launch ──────────────
    private fun buildBeginnerTimeline(): EventTimeline {
        val scope = supervisorNode("scope", "supervisorScope (+ CEH)", BuilderType.SupervisorScope,
            node("failing", "launch (fails)", BuilderType.Launch)
        )

        // The CEH is shown as a sibling tree — it's a context element, NOT a coroutine.
        // It "lights up" when invoked via an ExceptionEvent.
        val handler = node("ceh", "CoroutineExceptionHandler (context element, not a coroutine)", BuilderType.CoroutineScope)

        val events = listOf(
            NarrativeEvent(0, "A CoroutineExceptionHandler (CEH) is added to the scope's context. It's not a coroutine — it's a function that gets called for uncaught root exceptions. Drawn on the right for visibility."),
            StateChangeEvent(100, "scope Active", "scope", JobState.New, JobState.Active),
            StateChangeEvent(150, "CEH installed in context (ready to receive)", "ceh", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch starts", "failing", JobState.New, JobState.Active),
            NarrativeEvent(600, "The launch throws an uncaught RuntimeException..."),
            StateChangeEvent(800, "launch → Cancelling (exception)", "failing", JobState.Active, JobState.Cancelling),
            StateChangeEvent(900, "launch Cancelled", "failing", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1100, "Under SupervisorJob, the failure does NOT propagate up to the scope. Instead, it goes to the CEH (the installed handler for uncaught exceptions)."),
            ExceptionEvent(1300, "Exception delivered to CEH (handler is invoked synchronously with the exception)", "failing", "ceh", "RuntimeException"),
            NarrativeEvent(1500, "CEH ran — it might log the error, send to crash reporting, increment a metric, etc. The scope is unaffected and continues normally."),
            StateChangeEvent(1700, "scope Completing (no more children, no other work)", "scope", JobState.Active, JobState.Completing),
            StateChangeEvent(1800, "scope Completed", "scope", JobState.Completing, JobState.Completed),
            NarrativeEvent(2000, "Key: install the CEH on the scope (typically in CoroutineScope(SupervisorJob() + handler)). Installing it on individual launches is mostly useless — by the time the launch fails, its CEH is irrelevant.")
        )

        return timeline(
            tree = scope,
            secondTree = handler,
            events = events,
            kotlinCode = """
val handler = CoroutineExceptionHandler { _, throwable ->
    println("CEH caught: ${'$'}{throwable.message}")
    // typical usage: log it, send to Crashlytics / Sentry, increment a metric
}

suspend fun main() = supervisorScope {
    // The handler is installed on the scope's context.
    // (In practice you'd write CoroutineScope(SupervisorJob() + handler).)
    launch(handler) {
        throw RuntimeException("boom")
    }
    // Scope is unaffected — child failed, CEH was invoked.
}
            """.trimIndent()
        )
    }

    // ── Intermediate: launch vs async — CEH catches launch only ──────────
    private fun buildIntermediateTimeline(): EventTimeline {
        val scope = supervisorNode("scope", "supervisorScope (+ CEH)", BuilderType.SupervisorScope,
            node("launch-fails", "launch (fails)", BuilderType.Launch),
            node("async-fails", "async (fails — no await)", BuilderType.Async)
        )

        val handler = node("ceh", "CoroutineExceptionHandler", BuilderType.CoroutineScope)

        val events = listOf(
            NarrativeEvent(0, "Same scope, two children: a launch and an async. Both throw the SAME exception. The CEH only sees one of them. Which?"),
            StateChangeEvent(100, "scope Active", "scope", JobState.New, JobState.Active),
            StateChangeEvent(150, "CEH installed", "ceh", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch starts", "launch-fails", JobState.New, JobState.Active),
            StateChangeEvent(400, "async starts", "async-fails", JobState.New, JobState.Active),
            NarrativeEvent(700, "Both children throw RuntimeException at the same time."),
            StateChangeEvent(900, "launch → Cancelling", "launch-fails", JobState.Active, JobState.Cancelling),
            StateChangeEvent(950, "async → Cancelling", "async-fails", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1000, "launch Cancelled", "launch-fails", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1050, "async Cancelled", "async-fails", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1300, "launch's exception → CEH (caught!)", "launch-fails", "ceh", "RuntimeException"),
            NarrativeEvent(1500, "The async's exception is stored inside the Deferred — NOT delivered to the CEH. CEH is for fire-and-forget exceptions; async exceptions are 'return values' that you opt into via .await()."),
            NarrativeEvent(2000, "Since nobody called .await() on the async, its exception is silently lost — invisible to the CEH, invisible to .await(), invisible to logs."),
            StateChangeEvent(2200, "scope Completing (all children terminal)", "scope", JobState.Active, JobState.Completing),
            StateChangeEvent(2300, "scope Completed", "scope", JobState.Completing, JobState.Completed),
            NarrativeEvent(2500, "Rule: launch exceptions go to the CEH (fire-and-forget channel). async exceptions are held in the Deferred (opt-in channel via await). If you have a critical async, ALWAYS await it — otherwise its errors disappear.")
        )

        return timeline(
            tree = scope,
            secondTree = handler,
            events = events,
            kotlinCode = """
val handler = CoroutineExceptionHandler { _, throwable ->
    println("CEH caught: ${'$'}{throwable.message}")
}

suspend fun main() = supervisorScope {
    launch(handler) {
        throw RuntimeException("from launch")  // → CEH
    }

    async(handler) {
        throw RuntimeException("from async")   // → stored in Deferred
        // Nobody calls .await(), so this exception is SILENTLY LOST.
    }
}
            """.trimIndent()
        )
    }

    // ── Advanced: mixed children + nested propagation ────────────────────
    private fun buildAdvancedTimeline(): EventTimeline {
        val scope = supervisorNode("scope", "supervisorScope (+ CEH)", BuilderType.SupervisorScope,
            node("ok-launch", "launch (succeeds)", BuilderType.Launch),
            node("bad-launch", "launch (fails)", BuilderType.Launch),
            node("nest-parent", "launch (parent)", BuilderType.Launch,
                node("nest-child", "launch (nested, fails)", BuilderType.Launch)
            ),
            node("async-awaited", "async (fails, awaited)", BuilderType.Async),
            node("async-orphan", "async (fails, NEVER awaited)", BuilderType.Async)
        )

        val handler = node("ceh", "CoroutineExceptionHandler", BuilderType.CoroutineScope)

        val events = listOf(
            NarrativeEvent(0, "Five children. Watch which exceptions reach the CEH and which don't."),
            StateChangeEvent(100, "scope Active", "scope", JobState.New, JobState.Active),
            StateChangeEvent(150, "CEH installed", "ceh", JobState.New, JobState.Active),
            // Children start
            StateChangeEvent(300, "ok-launch starts", "ok-launch", JobState.New, JobState.Active),
            StateChangeEvent(350, "bad-launch starts", "bad-launch", JobState.New, JobState.Active),
            StateChangeEvent(400, "nest-parent starts", "nest-parent", JobState.New, JobState.Active),
            StateChangeEvent(450, "async-awaited starts", "async-awaited", JobState.New, JobState.Active),
            StateChangeEvent(500, "async-orphan starts", "async-orphan", JobState.New, JobState.Active),
            StateChangeEvent(600, "nested-child starts (inside nest-parent)", "nest-child", JobState.New, JobState.Active),

            // ok-launch completes normally
            StateChangeEvent(800, "ok-launch finishes", "ok-launch", JobState.Active, JobState.Completing),
            StateChangeEvent(900, "ok-launch Completed (no CEH event)", "ok-launch", JobState.Completing, JobState.Completed),
            NarrativeEvent(1000, "ok-launch succeeded — no exception, CEH not involved."),

            // bad-launch fails
            StateChangeEvent(1200, "bad-launch → Cancelling (threw)", "bad-launch", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1300, "bad-launch Cancelled", "bad-launch", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1500, "bad-launch's exception → CEH (root launch under SupervisorJob)", "bad-launch", "ceh", "RuntimeException: bad-launch"),

            // nest-child fails; propagates to nest-parent (which is the "root" from CEH's perspective under supervisor)
            NarrativeEvent(1700, "nested-child throws. It's NOT a direct child of the supervisor — its parent is nest-parent (a normal launch under supervisor). The exception goes UP to nest-parent first."),
            StateChangeEvent(1900, "nest-child → Cancelling", "nest-child", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2000, "nest-child Cancelled", "nest-child", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(2200, "Exception propagates UP from nest-child to nest-parent (normal structured cancellation)", "nest-child", "nest-parent", "RuntimeException: nest-child"),
            StateChangeEvent(2400, "nest-parent → Cancelling (received child failure)", "nest-parent", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2500, "nest-parent Cancelled (now THIS is the unhandled exception at the supervisor)", "nest-parent", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(2700, "nest-parent's exception (originally from nest-child) → CEH", "nest-parent", "ceh", "RuntimeException: nest-child"),

            // async-awaited fails (its exception goes via await — CEH NOT called)
            StateChangeEvent(2900, "async-awaited → Cancelling", "async-awaited", JobState.Active, JobState.Cancelling),
            StateChangeEvent(3000, "async-awaited Cancelled (exception in Deferred)", "async-awaited", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(3200, "Someone (in a try/catch) called async-awaited.await() — exception surfaces THERE, NOT in the CEH."),

            // async-orphan fails (silently lost — never awaited)
            StateChangeEvent(3400, "async-orphan → Cancelling", "async-orphan", JobState.Active, JobState.Cancelling),
            StateChangeEvent(3500, "async-orphan Cancelled (exception in Deferred, but nobody awaits)", "async-orphan", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(3700, "async-orphan's exception is now permanently lost — invisible to CEH, invisible to anyone (nobody calls await)."),

            // Scope completes
            StateChangeEvent(3900, "scope Completing", "scope", JobState.Active, JobState.Completing),
            StateChangeEvent(4000, "scope Completed", "scope", JobState.Completing, JobState.Completed),
            NarrativeEvent(4200, "Summary: CEH was invoked TWICE — once for bad-launch directly, once for nest-parent (which received nest-child's exception). Neither async invoked it. async-awaited surfaced via .await(); async-orphan was silently lost.")
        )

        return timeline(
            tree = scope,
            secondTree = handler,
            events = events,
            kotlinCode = """
val handler = CoroutineExceptionHandler { _, throwable ->
    println("CEH: ${'$'}{throwable.message}")
}

suspend fun main() = supervisorScope {
    launch(handler) {
        println("ok-launch done")
    }

    launch(handler) {
        throw RuntimeException("bad-launch")     // → CEH
    }

    launch(handler) {                            // nest-parent
        launch {                                 // nest-child
            throw RuntimeException("nest-child")
        }                                        // → propagates up to nest-parent
    }                                            // → CEH (nest-parent now unhandled)

    val awaited = async(handler) {
        throw RuntimeException("awaited")        // → stored in Deferred
    }
    try { awaited.await() } catch (e: Exception) { println("caught at await: ${'$'}{e.message}") }

    async(handler) {
        throw RuntimeException("orphan")         // → stored, NEVER surfaced
    }
}
            """.trimIndent()
        )
    }
}
