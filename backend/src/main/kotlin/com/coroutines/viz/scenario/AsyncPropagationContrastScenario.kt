package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class AsyncPropagationContrastScenario : Scenario {
    override val info = ScenarioInfo(
        id = "async-immediate-vs-deferred",
        name = "Async: Immediate vs Deferred",
        description = "Side-by-side: same async failure, different parent. Under coroutineScope the exception propagates IMMEDIATELY and cancels siblings. Under supervisorScope it is held in the Deferred and only surfaces when .await() is called — or is silently lost if nobody awaits.",
        category = "Comparison"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: just the contrast — exception goes up vs. doesn't ──────
    private fun buildBeginnerTimeline(): EventTimeline {
        val left = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-async", "async (fails)", BuilderType.Async)
        )

        val right = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-async", "async (fails)", BuilderType.Async)
        )

        val events = listOf(
            narrative(0, "Same code, two parents: LEFT is coroutineScope, RIGHT is supervisorScope. Both have one async that will fail. Nobody calls .await()."),
            starts(100, "LEFT: coroutineScope Active", "cs-root"),
            starts(150, "RIGHT: supervisorScope Active", "ss-root"),
            starts(300, "LEFT: async starts", "cs-async"),
            starts(350, "RIGHT: async starts", "ss-async"),
            narrative(700, "Both async children throw the same exception..."),
            cancelling(900, "LEFT: async fails → Cancelling", "cs-async"),
            cancelling(950, "RIGHT: async fails → Cancelling", "ss-async"),
            cancelled(1000, "LEFT: async Cancelled", "cs-async"),
            cancelled(1050, "RIGHT: async Cancelled", "ss-async"),
            // LEFT: exception propagates immediately to coroutineScope
            exception(1300, "LEFT: exception propagates UP to coroutineScope immediately (async under a normal Job)", "cs-async", "cs-root", "RuntimeException"),
            narrative(1400, "LEFT: coroutineScope received the exception — it cancels itself. No .await() was needed; propagation is automatic."),
            cancelling(1500, "LEFT: coroutineScope → Cancelling", "cs-root"),
            cancelled(1700, "LEFT: coroutineScope Cancelled (re-throws to caller)", "cs-root"),
            // RIGHT: nothing happens — exception is silently stored
            narrative(1800, "RIGHT: nothing happens to the supervisor. The exception is sitting in the Deferred, waiting for .await(). Nobody is going to call it."),
            narrative(2400, "RIGHT: all children are in terminal states (the failed async is Cancelled), so the supervisor can complete normally — taking the exception to the grave."),
            completing(2600, "RIGHT: supervisorScope → Completing", "ss-root"),
            completed(2700, "RIGHT: supervisorScope Completed (exception silently lost!)", "ss-root"),
            narrative(2900, "Takeaway: under coroutineScope, async failures propagate automatically. Under supervisorScope, they are held in the Deferred — you MUST call .await() to surface them. Forgetting to await is a silent-bug source.")
        )

        return timeline(
            tree = left,
            secondTree = right,
            events = events,
            kotlinCode = """
// LEFT — coroutineScope: async failure propagates immediately
suspend fun left() = coroutineScope {
    async {
        delay(100)
        throw RuntimeException("boom")
    }
    // No await(). Exception still propagates — scope is cancelled.
}

// RIGHT — supervisorScope: async failure is stored in the Deferred
suspend fun right() = supervisorScope {
    async {
        delay(100)
        throw RuntimeException("boom")
    }
    // No await(). Exception is silently lost!
}
            """.trimIndent()
        )
    }

    // ── Intermediate: add a sibling, observe what happens to it ──────────
    private fun buildIntermediateTimeline(): EventTimeline {
        val left = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-async", "async (fails)", BuilderType.Async),
            node("cs-sibling", "launch (sibling)", BuilderType.Launch)
        )

        val right = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-async", "async (fails)", BuilderType.Async),
            node("ss-sibling", "launch (sibling)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Same structure, different parents. Each side has an async (that will fail) and a launch sibling that wants to do useful work. Watch what happens to the sibling."),
            starts(100, "LEFT: coroutineScope Active", "cs-root"),
            starts(150, "RIGHT: supervisorScope Active", "ss-root"),
            starts(300, "LEFT: async starts", "cs-async"),
            starts(350, "RIGHT: async starts", "ss-async"),
            starts(400, "LEFT: sibling starts", "cs-sibling"),
            starts(450, "RIGHT: sibling starts", "ss-sibling"),
            narrative(700, "Both async children fail at the same time. The siblings are mid-work."),
            cancelling(900, "LEFT: async → Cancelling", "cs-async"),
            cancelling(950, "RIGHT: async → Cancelling", "ss-async"),
            cancelled(1000, "LEFT: async Cancelled", "cs-async"),
            cancelled(1050, "RIGHT: async Cancelled", "ss-async"),
            // LEFT side: propagation cascade
            exception(1200, "LEFT: exception propagates UP to coroutineScope IMMEDIATELY (no await needed)", "cs-async", "cs-root", "RuntimeException"),
            narrative(1300, "LEFT: coroutineScope cancels its remaining children. The sibling never gets to finish."),
            cancelling(1400, "LEFT: coroutineScope → Cancelling", "cs-root"),
            cancellation(1500, "LEFT: cancels sibling", "cs-root", "cs-sibling"),
            cancelling(1600, "LEFT: sibling → Cancelling", "cs-sibling"),
            cancelled(1800, "LEFT: sibling Cancelled (work lost!)", "cs-sibling"),
            cancelled(2000, "LEFT: coroutineScope Cancelled (re-throws to caller)", "cs-root"),
            // RIGHT side: sibling continues normally
            narrative(2100, "RIGHT: exception stored in Deferred. Supervisor doesn't see it. The sibling keeps working."),
            narrative(2400, "RIGHT: sibling still doing useful work..."),
            completing(2700, "RIGHT: sibling finishes its work", "ss-sibling"),
            completed(2800, "RIGHT: sibling Completed", "ss-sibling"),
            narrative(3000, "RIGHT: all children terminal. No .await() was called. Supervisor completes normally — the async exception was silently lost."),
            completing(3200, "RIGHT: supervisorScope → Completing", "ss-root"),
            completed(3300, "RIGHT: supervisorScope Completed", "ss-root"),
            narrative(3500, "Same exception, two outcomes: LEFT cancelled everything (siblings paid the price). RIGHT preserved the sibling's work but lost the error. Pick the parent that matches your intent.")
        )

        return timeline(
            tree = left,
            secondTree = right,
            events = events,
            kotlinCode = """
// LEFT — coroutineScope: sibling is cancelled when async fails
suspend fun left() = coroutineScope {
    async {
        delay(200)
        throw RuntimeException("boom")
    }
    launch {  // sibling
        delay(2000)
        println("sibling done")  // never reached
    }
    // Exception propagates immediately — sibling cancelled.
}

// RIGHT — supervisorScope: sibling completes, exception silently lost
suspend fun right() = supervisorScope {
    async {
        delay(200)
        throw RuntimeException("boom")
    }
    launch {  // sibling
        delay(2000)
        println("sibling done")  // runs!
    }
    // No await() — async failure is silently dropped.
}
            """.trimIndent()
        )
    }

    // ── Advanced: add await() — see the right side finally rethrow ──────
    private fun buildAdvancedTimeline(): EventTimeline {
        val left = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-async", "async (fails)", BuilderType.Async),
            node("cs-sibling-1", "launch #1", BuilderType.Launch),
            node("cs-sibling-2", "launch #2", BuilderType.Launch)
        )

        val right = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-async", "async (fails)", BuilderType.Async),
            node("ss-sibling-1", "launch #1", BuilderType.Launch),
            node("ss-sibling-2", "launch #2", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Now both sides call .await() at the end. Watch what reaches it and what doesn't."),
            // Both scopes start
            starts(100, "LEFT: coroutineScope Active", "cs-root"),
            starts(150, "RIGHT: supervisorScope Active", "ss-root"),
            // Children start
            starts(300, "LEFT: async starts", "cs-async"),
            starts(350, "RIGHT: async starts", "ss-async"),
            starts(400, "LEFT: launch #1 starts", "cs-sibling-1"),
            starts(450, "RIGHT: launch #1 starts", "ss-sibling-1"),
            starts(500, "LEFT: launch #2 starts", "cs-sibling-2"),
            starts(550, "RIGHT: launch #2 starts", "ss-sibling-2"),
            narrative(800, "Both async children fail at the same time. Two siblings on each side are mid-work, and .await() is scheduled to run after a longer delay."),
            // Both async fail
            cancelling(1000, "LEFT: async → Cancelling", "cs-async"),
            cancelling(1050, "RIGHT: async → Cancelling", "ss-async"),
            cancelled(1100, "LEFT: async Cancelled", "cs-async"),
            cancelled(1150, "RIGHT: async Cancelled", "ss-async"),
            // LEFT: cascade
            exception(1300, "LEFT: exception propagates UP immediately (before await is reached)", "cs-async", "cs-root", "RuntimeException"),
            narrative(1400, "LEFT: coroutineScope cancels both siblings. The .await() at the end of the body is never reached — the body itself is being cancelled."),
            cancelling(1500, "LEFT: coroutineScope → Cancelling", "cs-root"),
            cancellation(1600, "LEFT: cancels launch #1", "cs-root", "cs-sibling-1"),
            cancelling(1700, "LEFT: launch #1 → Cancelling", "cs-sibling-1"),
            cancellation(1750, "LEFT: cancels launch #2", "cs-root", "cs-sibling-2"),
            cancelling(1800, "LEFT: launch #2 → Cancelling", "cs-sibling-2"),
            cancelled(2000, "LEFT: launch #1 Cancelled", "cs-sibling-1"),
            cancelled(2050, "LEFT: launch #2 Cancelled", "cs-sibling-2"),
            cancelled(2200, "LEFT: coroutineScope Cancelled (re-throws to caller)", "cs-root"),
            // RIGHT: siblings continue, then await throws
            narrative(2300, "RIGHT: exception sitting in Deferred. Siblings continue normally."),
            completing(2600, "RIGHT: launch #1 completes", "ss-sibling-1"),
            completed(2700, "RIGHT: launch #1 Completed", "ss-sibling-1"),
            completing(2800, "RIGHT: launch #2 completes", "ss-sibling-2"),
            completed(2900, "RIGHT: launch #2 Completed", "ss-sibling-2"),
            narrative(3100, "RIGHT: now the body finally calls deferred.await(). This is when the stored exception is rethrown."),
            exception(3300, "RIGHT: .await() rethrows from supervisor's body — propagates UP to the caller", "ss-async", "ss-root", "RuntimeException"),
            cancelling(3400, "RIGHT: supervisorScope → Cancelling (its own body threw)", "ss-root"),
            cancelled(3600, "RIGHT: supervisorScope Cancelled (no siblings left to cancel — they already finished)", "ss-root"),
            narrative(3800, "Summary: LEFT loses sibling work; RIGHT preserves sibling work but the await still throws at the end. Both scopes end Cancelled in this case — but the WORK DONE before the throw is the critical difference.")
        )

        return timeline(
            tree = left,
            secondTree = right,
            events = events,
            kotlinCode = """
// LEFT — coroutineScope: siblings cancelled BEFORE await is reached
suspend fun left() = coroutineScope {
    val d = async {
        delay(200)
        throw RuntimeException("boom")
    }
    launch { delay(2000); println("L #1 done") }  // cancelled
    launch { delay(2000); println("L #2 done") }  // cancelled

    delay(1500)
    d.await()  // never reached — scope already cancelled
}

// RIGHT — supervisorScope: siblings complete, THEN await throws
suspend fun right() = supervisorScope {
    val d = async {
        delay(200)
        throw RuntimeException("boom")
    }
    launch { delay(2000); println("R #1 done") }  // runs!
    launch { delay(2000); println("R #2 done") }  // runs!

    delay(2500)
    d.await()  // throws here — scope cancelled, but siblings already finished
}
            """.trimIndent()
        )
    }
}
