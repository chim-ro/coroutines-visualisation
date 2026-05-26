package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class WithTimeoutOrNullScenario : Scenario {
    override val info = ScenarioInfo(
        id = "with-timeout-or-null",
        name = "withTimeoutOrNull",
        description = "The safer variant of withTimeout: instead of throwing TimeoutCancellationException, it returns null when the deadline expires. Use it when a timeout is an expected outcome (e.g., 'try the fast path, fall back if it's slow') — no try/catch required.",
        category = "Cancellation"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: one timeout block, exceeds deadline, returns null ─────
    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "timeout-block",
                    displayName = "withTimeoutOrNull(500)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "slow-work",
                            displayName = "launch (slow — needs 1000ms)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "withTimeoutOrNull is withTimeout's safer sibling: same cancellation behavior internally, but the function RETURNS NULL on timeout instead of throwing."),
            StateChangeEvent(100, "coroutineScope Active", "root", JobState.New, JobState.Active),
            StateChangeEvent(300, "withTimeoutOrNull(500) Active — the 500ms clock starts", "timeout-block", JobState.New, JobState.Active),
            StateChangeEvent(500, "Slow launch starts — needs 1000ms but only has 500ms", "slow-work", JobState.New, JobState.Active),
            NarrativeEvent(800, "Clock ticking... slow work is mid-delay."),
            NarrativeEvent(1100, "500ms deadline expired! withTimeoutOrNull cancels the block."),
            CancellationEvent(1300, "timeout cancels slow-work", "timeout-block", "slow-work"),
            StateChangeEvent(1500, "slow-work → Cancelling", "slow-work", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1700, "slow-work Cancelled (delay was interrupted)", "slow-work", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1900, "withTimeoutOrNull's internal Job → Cancelling", "timeout-block", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2100, "withTimeoutOrNull's internal Job Cancelled — function catches the TimeoutCancellationException and RETURNS NULL to the caller", "timeout-block", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2300, "Crucially: no exception propagated to coroutineScope. From outside, the call was `val result = withTimeoutOrNull(500) { ... }` and result is just `null`. No try/catch."),
            StateChangeEvent(2500, "coroutineScope continues normally → Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(2600, "coroutineScope Completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(2800, "Use withTimeoutOrNull when a timeout is an expected outcome you want to handle inline. Use withTimeout when a timeout is exceptional and the caller should know via an exception.")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    val result: String? = withTimeoutOrNull(500L) {
        delay(1000L)              // exceeds the 500ms deadline
        "expensive result"        // never returned
    }

    // No try/catch needed — just check for null.
    println(result ?: "fallback (timed out)")
}
            """.trimIndent()
        )
    }

    // ── Intermediate: one call succeeds, one times out — caller handles both ──
    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "coroutineScope",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "fast-timeout",
                    displayName = "withTimeoutOrNull(500) — fast",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "fast-work",
                            displayName = "launch (200ms work)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                ),
                CoroutineNode(
                    id = "slow-timeout",
                    displayName = "withTimeoutOrNull(500) — slow",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "slow-work",
                            displayName = "launch (1000ms work)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Two sequential withTimeoutOrNull calls. First call's work fits in time → returns the value. Second call's work doesn't → returns null. Same caller code handles both."),
            StateChangeEvent(100, "coroutineScope Active", "root", JobState.New, JobState.Active),
            // First call — fast
            StateChangeEvent(200, "First withTimeoutOrNull(500) Active", "fast-timeout", JobState.New, JobState.Active),
            StateChangeEvent(300, "Fast work starts (needs 200ms)", "fast-work", JobState.New, JobState.Active),
            StateChangeEvent(600, "Fast work finishes well within the deadline", "fast-work", JobState.Active, JobState.Completing),
            StateChangeEvent(700, "Fast work Completed", "fast-work", JobState.Completing, JobState.Completed),
            StateChangeEvent(900, "withTimeoutOrNull block returns the value (no timeout)", "fast-timeout", JobState.Active, JobState.Completing),
            StateChangeEvent(1000, "First call Completed — caller got a non-null result", "fast-timeout", JobState.Completing, JobState.Completed),
            NarrativeEvent(1200, "First call returned the actual value (e.g., \"fast result\"). Now the slow call starts."),
            // Second call — slow / times out
            StateChangeEvent(1400, "Second withTimeoutOrNull(500) Active — its OWN clock starts", "slow-timeout", JobState.New, JobState.Active),
            StateChangeEvent(1600, "Slow work starts (needs 1000ms — won't fit)", "slow-work", JobState.New, JobState.Active),
            NarrativeEvent(2000, "500ms deadline expires while slow-work is mid-delay."),
            CancellationEvent(2200, "Second timeout cancels slow-work", "slow-timeout", "slow-work"),
            StateChangeEvent(2400, "slow-work → Cancelling", "slow-work", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2600, "slow-work Cancelled", "slow-work", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(2800, "Second withTimeoutOrNull block → Cancelling", "slow-timeout", JobState.Active, JobState.Cancelling),
            StateChangeEvent(2900, "Second call Cancelled internally — function returns NULL", "slow-timeout", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(3100, "Caller code is the same shape for both: `val r = withTimeoutOrNull(500) { ... }; r ?: fallback`. First time r is the value, second time r is null."),
            StateChangeEvent(3300, "coroutineScope Completing", "root", JobState.Active, JobState.Completing),
            StateChangeEvent(3400, "coroutineScope Completed", "root", JobState.Completing, JobState.Completed),
            NarrativeEvent(3600, "Pattern: try-fast-then-fallback. withTimeoutOrNull lets you express this without exception handling boilerplate.")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    // First call: completes in time
    val fast = withTimeoutOrNull(500L) {
        delay(200L)
        "fast result"
    }
    println(fast ?: "fast: timed out")    // prints: fast result

    // Second call: times out
    val slow = withTimeoutOrNull(500L) {
        delay(1000L)
        "slow result"                     // never returned
    }
    println(slow ?: "slow: timed out")    // prints: slow: timed out
}
            """.trimIndent()
        )
    }

    // ── Advanced: side-by-side — withTimeout (throws) vs withTimeoutOrNull (returns null) ──
    private fun buildAdvancedTimeline(): EventTimeline {
        // LEFT — withTimeout: caller needs try/catch
        val withScope = CoroutineNode(
            id = "wt-scope",
            displayName = "coroutineScope (LEFT)",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "wt-block",
                    displayName = "withTimeout(500)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "wt-work",
                            displayName = "launch (slow)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        // RIGHT — withTimeoutOrNull: caller just checks for null
        val orNullScope = CoroutineNode(
            id = "or-scope",
            displayName = "coroutineScope (RIGHT)",
            builder = BuilderType.CoroutineScope,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "or-block",
                    displayName = "withTimeoutOrNull(500)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "or-work",
                            displayName = "launch (slow)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(0, "Same body, same timeout. LEFT uses withTimeout (throws on timeout — caller MUST try/catch). RIGHT uses withTimeoutOrNull (returns null — caller just checks). Watch the internal Job states — they're nearly identical. The difference is purely caller-facing."),
            // Both scopes start
            StateChangeEvent(100, "LEFT: scope Active", "wt-scope", JobState.New, JobState.Active),
            StateChangeEvent(150, "RIGHT: scope Active", "or-scope", JobState.New, JobState.Active),
            // Timeout blocks start
            StateChangeEvent(300, "LEFT: withTimeout block Active", "wt-block", JobState.New, JobState.Active),
            StateChangeEvent(350, "RIGHT: withTimeoutOrNull block Active", "or-block", JobState.New, JobState.Active),
            // Slow work starts on both
            StateChangeEvent(500, "LEFT: slow work starts", "wt-work", JobState.New, JobState.Active),
            StateChangeEvent(550, "RIGHT: slow work starts", "or-work", JobState.New, JobState.Active),
            NarrativeEvent(900, "Both timeouts fire at the same instant. The internal cancellation is the same."),
            // Both timeouts fire
            CancellationEvent(1100, "LEFT: timeout cancels slow work", "wt-block", "wt-work"),
            CancellationEvent(1150, "RIGHT: timeout cancels slow work", "or-block", "or-work"),
            StateChangeEvent(1300, "LEFT: slow work → Cancelling", "wt-work", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1350, "RIGHT: slow work → Cancelling", "or-work", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1500, "LEFT: slow work Cancelled", "wt-work", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1550, "RIGHT: slow work Cancelled", "or-work", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1700, "LEFT: withTimeout block → Cancelling", "wt-block", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1750, "RIGHT: withTimeoutOrNull block → Cancelling", "or-block", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1900, "LEFT: withTimeout block Cancelled — RE-THROWS TimeoutCancellationException to caller", "wt-block", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1950, "RIGHT: withTimeoutOrNull block Cancelled — caught internally, RETURNS NULL", "or-block", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2100, "LEFT: exception escapes withTimeout. The caller's try/catch absorbs it; without it, the exception would propagate up to coroutineScope and cancel it."),
            NarrativeEvent(2400, "RIGHT: no exception. Caller's `val r = withTimeoutOrNull(...)` is just null. Code continues normally with `r ?: fallback`."),
            // Both scopes complete normally (LEFT because of try/catch; RIGHT because no exception)
            StateChangeEvent(2700, "LEFT: scope continues normally (try/catch handled the exception)", "wt-scope", JobState.Active, JobState.Completing),
            StateChangeEvent(2750, "RIGHT: scope continues normally (no exception in the first place)", "or-scope", JobState.Active, JobState.Completing),
            StateChangeEvent(2900, "LEFT: scope Completed", "wt-scope", JobState.Completing, JobState.Completed),
            StateChangeEvent(2950, "RIGHT: scope Completed", "or-scope", JobState.Completing, JobState.Completed),
            NarrativeEvent(3100, "Same internal behavior, very different ergonomics. Rule of thumb: if timeout is expected → withTimeoutOrNull. If timeout is an error worth propagating → withTimeout (and let it throw).")
        )

        return EventTimeline(
            scenarioName = info.name,
            tree = withScope,
            secondTree = orNullScope,
            events = events,
            kotlinCode = """
// LEFT — withTimeout: caller MUST try/catch
suspend fun left() = coroutineScope {
    try {
        val r = withTimeout(500L) {
            delay(1000L)
            "value"                  // never returned
        }
        println(r)
    } catch (e: TimeoutCancellationException) {
        println("timed out")
    }
}

// RIGHT — withTimeoutOrNull: caller just checks
suspend fun right() = coroutineScope {
    val r: String? = withTimeoutOrNull(500L) {
        delay(1000L)
        "value"                      // never returned
    }
    println(r ?: "timed out")
}
            """.trimIndent()
        )
    }
}
