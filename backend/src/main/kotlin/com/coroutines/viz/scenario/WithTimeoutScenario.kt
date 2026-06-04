package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class WithTimeoutScenario : Scenario {
    override val info = ScenarioInfo(
        id = "with-timeout",
        name = "withTimeout / withTimeoutOrNull",
        description = "withTimeout cancels coroutines that exceed the time limit and re-throws TimeoutCancellationException to the caller. The advanced level contrasts it side-by-side with withTimeoutOrNull, which returns null instead of throwing.",
        category = "Cancellation"
    )

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()
        "beginner" -> buildBeginnerTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("timeout-scope", "withTimeout(500)", BuilderType.CoroutineScope,
                node("slow-work", "launch (slow)", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "withTimeout sets a 500ms deadline. If the child doesn't finish in time, it gets cancelled."),
            starts(100, "coroutineScope starts execution", "root"),
            starts(300, "withTimeout scope becomes active — the 500ms clock starts now", "timeout-scope"),
            starts(500, "Slow launch starts — it needs 1000ms but only has 500ms left", "slow-work"),
            narrative(800, "The clock is ticking... the slow child is still working but time is running out."),
            narrative(1100, "Timeout fires! 500ms have elapsed. The slow child is still running — TimeoutCancellationException is thrown."),
            cancellation(1300, "withTimeout cancels slow child — TimeoutCancellationException", "timeout-scope", "slow-work"),
            cancelling(1500, "Slow launch enters cancelling state due to timeout", "slow-work"),
            cancelled(1700, "Slow launch is fully cancelled", "slow-work"),
            cancelling(1900, "withTimeout scope is cancelled because the deadline expired", "timeout-scope"),
            cancelled(2100, "withTimeout scope fully cancelled", "timeout-scope"),
            completing(2300, "Scope continues — the surrounding try/catch absorbed TimeoutCancellationException (without it, the exception would propagate up to the scope's caller)", "root"),
            completed(2500, "Scope fully completed", "root"),
            narrative(2700, "Key takeaway: withTimeout cancels children that exceed the time limit by throwing TimeoutCancellationException — a subtype of CancellationException."),
            narrative(2900, "Safer alternative: withTimeoutOrNull(500L) { ... } returns null instead of throwing, so no try/catch is needed for the common 'maybe-timed-out' case.")
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            suspend fun main() = coroutineScope {
                try {
                    withTimeout(500L) {
                        launch {
                            println("Slow work started")
                            delay(1000L) // exceeds the 500ms timeout!
                            println("Done") // never reached
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    println("Timed out!")
                }
                println("Program continues after timeout")

                // Safer alternative — no try/catch needed:
                val result = withTimeoutOrNull(500L) {
                    delay(1000L)
                    "result"
                }
                println(result) // null
            }
        """.trimIndent()

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
        )
    }

    // Advanced: side-by-side — withTimeout (throws) vs withTimeoutOrNull (returns null).
    // (Folded in from the former WithTimeoutOrNullScenario.)
    private fun buildAdvancedTimeline(): EventTimeline {
        // LEFT — withTimeout: caller needs try/catch
        val withScope = node("wt-scope", "coroutineScope (LEFT)", BuilderType.CoroutineScope,
            node("wt-block", "withTimeout(500)", BuilderType.CoroutineScope,
                node("wt-work", "launch (slow)", BuilderType.Launch)
            )
        )

        // RIGHT — withTimeoutOrNull: caller just checks for null
        val orNullScope = node("or-scope", "coroutineScope (RIGHT)", BuilderType.CoroutineScope,
            node("or-block", "withTimeoutOrNull(500)", BuilderType.CoroutineScope,
                node("or-work", "launch (slow)", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "Same body, same timeout. LEFT uses withTimeout (throws on timeout — caller MUST try/catch). RIGHT uses withTimeoutOrNull (returns null — caller just checks). Watch the internal Job states — they're nearly identical. The difference is purely caller-facing."),
            // Both scopes start
            starts(100, "LEFT: scope Active", "wt-scope"),
            starts(150, "RIGHT: scope Active", "or-scope"),
            // Timeout blocks start
            starts(300, "LEFT: withTimeout block Active", "wt-block"),
            starts(350, "RIGHT: withTimeoutOrNull block Active", "or-block"),
            // Slow work starts on both
            starts(500, "LEFT: slow work starts", "wt-work"),
            starts(550, "RIGHT: slow work starts", "or-work"),
            narrative(900, "Both timeouts fire at the same instant. The internal cancellation is the same."),
            // Both timeouts fire
            cancellation(1100, "LEFT: timeout cancels slow work", "wt-block", "wt-work"),
            cancellation(1150, "RIGHT: timeout cancels slow work", "or-block", "or-work"),
            cancelling(1300, "LEFT: slow work → Cancelling", "wt-work"),
            cancelling(1350, "RIGHT: slow work → Cancelling", "or-work"),
            cancelled(1500, "LEFT: slow work Cancelled", "wt-work"),
            cancelled(1550, "RIGHT: slow work Cancelled", "or-work"),
            cancelling(1700, "LEFT: withTimeout block → Cancelling", "wt-block"),
            cancelling(1750, "RIGHT: withTimeoutOrNull block → Cancelling", "or-block"),
            cancelled(1900, "LEFT: withTimeout block Cancelled — RE-THROWS TimeoutCancellationException to caller", "wt-block"),
            cancelled(1950, "RIGHT: withTimeoutOrNull block Cancelled — caught internally, RETURNS NULL", "or-block"),
            narrative(2100, "LEFT: exception escapes withTimeout. The caller's try/catch absorbs it; without it, the exception would propagate up to coroutineScope and cancel it."),
            narrative(2400, "RIGHT: no exception. Caller's `val r = withTimeoutOrNull(...)` is just null. Code continues normally with `r ?: fallback`."),
            // Both scopes complete normally (LEFT because of try/catch; RIGHT because no exception)
            completing(2700, "LEFT: scope continues normally (try/catch handled the exception)", "wt-scope"),
            completing(2750, "RIGHT: scope continues normally (no exception in the first place)", "or-scope"),
            completed(2900, "LEFT: scope Completed", "wt-scope"),
            completed(2950, "RIGHT: scope Completed", "or-scope"),
            narrative(3100, "Same internal behavior, very different ergonomics. Rule of thumb: if timeout is expected → withTimeoutOrNull. If timeout is an error worth propagating → withTimeout (and let it throw).")
        )

        return timeline(
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

    override fun buildTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("timeout-scope", "withTimeout(1000)", BuilderType.CoroutineScope,
                node("slow-work", "launch (slow)", BuilderType.Launch),
                node("fast-work", "async (fast)", BuilderType.Async)
            )
        )

        val events = listOf(
            narrative(0, "withTimeout sets a 1000ms deadline. All children must complete within the time limit or be cancelled."),
            starts(100, "coroutineScope starts execution", "root"),
            starts(300, "withTimeout scope becomes active — the 1000ms clock starts ticking", "timeout-scope"),
            starts(500, "Slow launch starts — it needs 2000ms to finish (too long!)", "slow-work"),
            starts(700, "Fast async starts — it only needs 500ms", "fast-work"),
            narrative(1000, "Both children are running. The fast async will finish in time, but the slow launch won't..."),
            completing(1200, "Fast async completes its work within the timeout", "fast-work"),
            completed(1400, "Fast async fully completed", "fast-work"),
            narrative(1600, "Timeout fires! 1000ms have elapsed. The slow launch is still running — TimeoutCancellationException is thrown."),
            cancellation(1800, "withTimeout cancels slow launch — TimeoutCancellationException", "timeout-scope", "slow-work"),
            cancelling(2000, "Slow launch enters cancelling state due to timeout", "slow-work"),
            cancelled(2200, "Slow launch is fully cancelled", "slow-work"),
            cancelling(2400, "withTimeout scope is cancelled due to the timeout", "timeout-scope"),
            cancelled(2600, "withTimeout scope fully cancelled", "timeout-scope"),
            narrative(2800, "TimeoutCancellationException is a CancellationException subclass — but withTimeout re-throws it to its caller. Without a try/catch, it would propagate up."),
            completing(3000, "Scope continues — the surrounding try/catch absorbed TimeoutCancellationException", "root"),
            completed(3200, "Scope fully completed", "root"),
            narrative(3400, "Key insight: withTimeout throws TimeoutCancellationException (a subclass of CancellationException). Use withTimeoutOrNull to get null instead of an exception.")
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            suspend fun main() = coroutineScope {
                try {
                    withTimeout(1000L) {
                        // launch (slow) — takes too long
                        launch {
                            println("Slow work started")
                            delay(2000L) // exceeds timeout!
                            println("Slow work done") // never reached
                        }

                        // async (fast) — completes in time
                        val result = async {
                            delay(500L)
                            "fast result"
                        }

                        println("Fast result: ${'$'}{result.await()}")
                    }
                } catch (e: TimeoutCancellationException) {
                    println("Timed out!")
                }

                println("Scope continues after timeout")
            }
        """.trimIndent()

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
        )
    }
}
