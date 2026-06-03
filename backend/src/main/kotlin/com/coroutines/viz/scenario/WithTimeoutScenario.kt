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
            NarrativeEvent(
                delayMs = 0,
                description = "withTimeout sets a 500ms deadline. If the child doesn't finish in time, it gets cancelled."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "coroutineScope starts execution",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 300,
                description = "withTimeout scope becomes active — the 500ms clock starts now",
                nodeId = "timeout-scope",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 500,
                description = "Slow launch starts — it needs 1000ms but only has 500ms left",
                nodeId = "slow-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 800,
                description = "The clock is ticking... the slow child is still working but time is running out."
            ),
            NarrativeEvent(
                delayMs = 1100,
                description = "Timeout fires! 500ms have elapsed. The slow child is still running — TimeoutCancellationException is thrown."
            ),
            CancellationEvent(
                delayMs = 1300,
                description = "withTimeout cancels slow child — TimeoutCancellationException",
                sourceNodeId = "timeout-scope",
                targetNodeId = "slow-work"
            ),
            StateChangeEvent(
                delayMs = 1500,
                description = "Slow launch enters cancelling state due to timeout",
                nodeId = "slow-work",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 1700,
                description = "Slow launch is fully cancelled",
                nodeId = "slow-work",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 1900,
                description = "withTimeout scope is cancelled because the deadline expired",
                nodeId = "timeout-scope",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 2100,
                description = "withTimeout scope fully cancelled",
                nodeId = "timeout-scope",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 2300,
                description = "Scope continues — the surrounding try/catch absorbed TimeoutCancellationException (without it, the exception would propagate up to the scope's caller)",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2500,
                description = "Scope fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 2700,
                description = "Key takeaway: withTimeout cancels children that exceed the time limit by throwing TimeoutCancellationException — a subtype of CancellationException."
            ),
            NarrativeEvent(
                delayMs = 2900,
                description = "Safer alternative: withTimeoutOrNull(500L) { ... } returns null instead of throwing, so no try/catch is needed for the common 'maybe-timed-out' case."
            )
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
            NarrativeEvent(
                delayMs = 0,
                description = "withTimeout sets a 1000ms deadline. All children must complete within the time limit or be cancelled."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "coroutineScope starts execution",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 300,
                description = "withTimeout scope becomes active — the 1000ms clock starts ticking",
                nodeId = "timeout-scope",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 500,
                description = "Slow launch starts — it needs 2000ms to finish (too long!)",
                nodeId = "slow-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 700,
                description = "Fast async starts — it only needs 500ms",
                nodeId = "fast-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1000,
                description = "Both children are running. The fast async will finish in time, but the slow launch won't..."
            ),
            StateChangeEvent(
                delayMs = 1200,
                description = "Fast async completes its work within the timeout",
                nodeId = "fast-work",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 1400,
                description = "Fast async fully completed",
                nodeId = "fast-work",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 1600,
                description = "Timeout fires! 1000ms have elapsed. The slow launch is still running — TimeoutCancellationException is thrown."
            ),
            CancellationEvent(
                delayMs = 1800,
                description = "withTimeout cancels slow launch — TimeoutCancellationException",
                sourceNodeId = "timeout-scope",
                targetNodeId = "slow-work"
            ),
            StateChangeEvent(
                delayMs = 2000,
                description = "Slow launch enters cancelling state due to timeout",
                nodeId = "slow-work",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 2200,
                description = "Slow launch is fully cancelled",
                nodeId = "slow-work",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 2400,
                description = "withTimeout scope is cancelled due to the timeout",
                nodeId = "timeout-scope",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 2600,
                description = "withTimeout scope fully cancelled",
                nodeId = "timeout-scope",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            NarrativeEvent(
                delayMs = 2800,
                description = "TimeoutCancellationException is a CancellationException subclass — but withTimeout re-throws it to its caller. Without a try/catch, it would propagate up."
            ),
            StateChangeEvent(
                delayMs = 3000,
                description = "Scope continues — the surrounding try/catch absorbed TimeoutCancellationException",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3200,
                description = "Scope fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 3400,
                description = "Key insight: withTimeout throws TimeoutCancellationException (a subclass of CancellationException). Use withTimeoutOrNull to get null instead of an exception."
            )
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
