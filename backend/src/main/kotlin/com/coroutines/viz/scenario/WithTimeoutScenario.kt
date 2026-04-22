package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class WithTimeoutScenario : Scenario {
    override val info = ScenarioInfo(
        id = "with-timeout",
        name = "withTimeout Cancellation",
        description = "Demonstrates how withTimeout cancels coroutines that exceed the time limit, throwing TimeoutCancellationException.",
        category = "Cancellation"
    )

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()
        "beginner" -> buildBeginnerTimeline()
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
                    id = "timeout-scope",
                    displayName = "withTimeout(500)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "slow-work",
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
            NarrativeEvent(
                delayMs = 0,
                description = "withTimeout sets a 500ms deadline. If the child doesn't finish in time, it gets cancelled."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "runBlocking starts execution",
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
            ExceptionEvent(
                delayMs = 1300,
                description = "TimeoutCancellationException propagates from withTimeout scope to the slow child",
                sourceNodeId = "timeout-scope",
                targetNodeId = "slow-work",
                exceptionMessage = "TimeoutCancellationException: Timed out waiting for 500 ms"
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
                description = "runBlocking completes normally — the timeout exception doesn't propagate past withTimeout",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2500,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 2700,
                description = "Key takeaway: withTimeout cancels children that exceed the time limit by throwing TimeoutCancellationException."
            )
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            fun main() = runBlocking {
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
            }
        """.trimIndent()

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
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
                    id = "timeout-scope",
                    displayName = "withTimeout(1000)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "fast-work",
                            displayName = "launch (fast)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "medium-work",
                            displayName = "async (medium)",
                            builder = BuilderType.Async,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "slow-work",
                            displayName = "launch (slow)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New,
                            children = listOf(
                                CoroutineNode(
                                    id = "nested-timeout",
                                    displayName = "withTimeout(200)",
                                    builder = BuilderType.CoroutineScope,
                                    jobType = JobType.Job,
                                    initialState = JobState.New,
                                    children = listOf(
                                        CoroutineNode(
                                            id = "very-slow-work",
                                            displayName = "launch (very slow)",
                                            builder = BuilderType.Launch,
                                            jobType = JobType.Job,
                                            initialState = JobState.New
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val events = listOf(
            NarrativeEvent(
                delayMs = 0,
                description = "Outer withTimeout sets a 1000ms deadline. Inside, three children run concurrently. The slow child has its own nested withTimeout(200)."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "runBlocking starts execution",
                nodeId = "root",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 250,
                description = "Outer withTimeout(1000) scope becomes active — the 1000ms clock starts",
                nodeId = "timeout-scope",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 400,
                description = "Fast launch starts — needs only 300ms",
                nodeId = "fast-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 500,
                description = "Medium async starts — needs 900ms (just barely fits in the 1000ms timeout)",
                nodeId = "medium-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 600,
                description = "Slow launch starts — needs 1500ms (will exceed the outer timeout)",
                nodeId = "slow-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 750,
                description = "Nested withTimeout(200) scope starts inside the slow launch — its own 200ms clock begins",
                nodeId = "nested-timeout",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            StateChangeEvent(
                delayMs = 900,
                description = "Very slow launch starts inside the nested timeout — needs 500ms but only has 200ms",
                nodeId = "very-slow-work",
                fromState = JobState.New,
                toState = JobState.Active
            ),
            NarrativeEvent(
                delayMs = 1100,
                description = "The nested withTimeout(200) fires first! The very slow child has exceeded its 200ms budget."
            ),
            ExceptionEvent(
                delayMs = 1250,
                description = "Nested TimeoutCancellationException cancels the very slow child",
                sourceNodeId = "nested-timeout",
                targetNodeId = "very-slow-work",
                exceptionMessage = "TimeoutCancellationException: Timed out waiting for 200 ms"
            ),
            StateChangeEvent(
                delayMs = 1400,
                description = "Very slow launch enters cancelling state",
                nodeId = "very-slow-work",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 1550,
                description = "Very slow launch is fully cancelled",
                nodeId = "very-slow-work",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 1700,
                description = "Nested withTimeout scope is cancelled",
                nodeId = "nested-timeout",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 1850,
                description = "Nested withTimeout scope fully cancelled",
                nodeId = "nested-timeout",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            NarrativeEvent(
                delayMs = 2000,
                description = "The nested timeout was handled locally. The slow launch catches it and continues. Meanwhile, the fast child is about to finish."
            ),
            StateChangeEvent(
                delayMs = 2150,
                description = "Fast launch completes its work well within the outer timeout",
                nodeId = "fast-work",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2300,
                description = "Fast launch fully completed",
                nodeId = "fast-work",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            StateChangeEvent(
                delayMs = 2500,
                description = "Medium async completes just in time — it finishes right before the outer timeout deadline",
                nodeId = "medium-work",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 2650,
                description = "Medium async fully completed",
                nodeId = "medium-work",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 2800,
                description = "Outer timeout fires! 1000ms have elapsed. The slow launch is still running — it gets cancelled by the outer withTimeout."
            ),
            ExceptionEvent(
                delayMs = 2950,
                description = "Outer TimeoutCancellationException cancels the slow launch",
                sourceNodeId = "timeout-scope",
                targetNodeId = "slow-work",
                exceptionMessage = "TimeoutCancellationException: Timed out waiting for 1000 ms"
            ),
            StateChangeEvent(
                delayMs = 3100,
                description = "Slow launch enters cancelling state due to outer timeout",
                nodeId = "slow-work",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 3250,
                description = "Slow launch is fully cancelled",
                nodeId = "slow-work",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 3400,
                description = "Outer withTimeout scope enters cancelling state",
                nodeId = "timeout-scope",
                fromState = JobState.Active,
                toState = JobState.Cancelling
            ),
            StateChangeEvent(
                delayMs = 3550,
                description = "Outer withTimeout scope fully cancelled",
                nodeId = "timeout-scope",
                fromState = JobState.Cancelling,
                toState = JobState.Cancelled
            ),
            StateChangeEvent(
                delayMs = 3700,
                description = "runBlocking completes normally — timeout exceptions don't propagate past the withTimeout boundary",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3850,
                description = "runBlocking fully completed",
                nodeId = "root",
                fromState = JobState.Completing,
                toState = JobState.Completed
            ),
            NarrativeEvent(
                delayMs = 4000,
                description = "Key insights: Nested withTimeout scopes have independent clocks. The inner timeout fires first without affecting siblings. The outer timeout later cancels remaining children. TimeoutCancellationException stays within its withTimeout boundary."
            )
        )

        val kotlinCode = """
            import kotlinx.coroutines.*

            fun main() = runBlocking {
                try {
                    withTimeout(1000L) {
                        // Fast child — completes well within the deadline
                        launch {
                            delay(300L)
                            println("Fast work done")
                        }

                        // Medium child — completes just in time
                        val result = async {
                            delay(900L)
                            "medium result"
                        }

                        // Slow child — will be cancelled by outer timeout
                        launch {
                            // Nested timeout with its own shorter deadline
                            try {
                                withTimeout(200L) {
                                    launch {
                                        delay(500L) // exceeds nested 200ms timeout
                                        println("Very slow done") // never reached
                                    }
                                }
                            } catch (e: TimeoutCancellationException) {
                                println("Nested timeout caught: ${'$'}{e.message}")
                            }

                            delay(1500L) // exceeds outer 1000ms timeout
                            println("Slow work done") // never reached
                        }

                        println("Medium result: ${'$'}{result.await()}")
                    }
                } catch (e: TimeoutCancellationException) {
                    println("Outer timeout: ${'$'}{e.message}")
                }

                println("Program continues after all timeouts")
            }
        """.trimIndent()

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
        )
    }

    override fun buildTimeline(): EventTimeline {
        val tree = CoroutineNode(
            id = "root",
            displayName = "runBlocking",
            builder = BuilderType.RunBlocking,
            jobType = JobType.Job,
            initialState = JobState.New,
            children = listOf(
                CoroutineNode(
                    id = "timeout-scope",
                    displayName = "withTimeout(1000)",
                    builder = BuilderType.CoroutineScope,
                    jobType = JobType.Job,
                    initialState = JobState.New,
                    children = listOf(
                        CoroutineNode(
                            id = "slow-work",
                            displayName = "launch (slow)",
                            builder = BuilderType.Launch,
                            jobType = JobType.Job,
                            initialState = JobState.New
                        ),
                        CoroutineNode(
                            id = "fast-work",
                            displayName = "async (fast)",
                            builder = BuilderType.Async,
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
                description = "withTimeout sets a 1000ms deadline. All children must complete within the time limit or be cancelled."
            ),
            StateChangeEvent(
                delayMs = 100,
                description = "runBlocking starts execution",
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
                description = "⏰ Timeout fires! 1000ms have elapsed. The slow launch is still running — TimeoutCancellationException is thrown."
            ),
            ExceptionEvent(
                delayMs = 1800,
                description = "TimeoutCancellationException propagates from withTimeout scope to slow launch",
                sourceNodeId = "timeout-scope",
                targetNodeId = "slow-work",
                exceptionMessage = "TimeoutCancellationException: Timed out waiting for 1000 ms"
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
                description = "The TimeoutCancellationException is a CancellationException subclass, so it cancels the scope but does NOT crash the parent."
            ),
            StateChangeEvent(
                delayMs = 3000,
                description = "runBlocking completes normally — TimeoutCancellationException doesn't propagate past the withTimeout boundary",
                nodeId = "root",
                fromState = JobState.Active,
                toState = JobState.Completing
            ),
            StateChangeEvent(
                delayMs = 3200,
                description = "runBlocking fully completed",
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

            fun main() = runBlocking {
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

                println("runBlocking continues after timeout")
            }
        """.trimIndent()

        return EventTimeline(
            scenarioName = info.name,
            tree = tree,
            events = events,
            kotlinCode = kotlinCode
        )
    }
}
