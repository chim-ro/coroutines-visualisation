package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class NestedScopesScenario : Scenario {
    override val info = ScenarioInfo(
        id = "nested-scopes",
        name = "Nested Scopes",
        description = "Deep tree (4 levels) with mixed scope types showing how structured concurrency composes.",
        category = "Advanced"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            supervisorNode("supervisor", "supervisorScope", BuilderType.SupervisorScope,
                node("branch-a", "launch A", BuilderType.Launch,
                    node("a1", "async A1", BuilderType.Async),
                    node("a2", "async A2", BuilderType.Async)
                ),
                node("branch-b", "launch B (fails)", BuilderType.Launch,
                    node("b1", "launch B1", BuilderType.Launch)
                ),
                node("branch-c", "launch C", BuilderType.Launch)
            )
        )

        val events = listOf(
            narrative(0, "Deep nested structure: coroutineScope > supervisorScope > 3 branches"),
            starts(100, "Outer coroutineScope starts", "root"),
            starts(200, "supervisorScope starts", "supervisor"),
            starts(400, "Launch A starts", "branch-a"),
            starts(500, "Launch B starts", "branch-b"),
            starts(600, "Launch C starts", "branch-c"),
            starts(700, "Async A1 starts", "a1"),
            starts(800, "Async A2 starts", "a2"),
            starts(900, "Launch B1 starts", "b1"),
            narrative(1100, "Branch B encounters an error — exception propagates within B's subtree"),
            cancelling(1300, "B1 fails", "b1"),
            cancelled(1400, "B1 cancelled", "b1"),
            exception(1500, "Exception from B1 propagates up to launch B", "b1", "branch-b", "IOException"),
            cancelling(1600, "Launch B enters Cancelling", "branch-b"),
            cancelled(1700, "Launch B cancelled", "branch-b"),
            narrative(1800, "Exception from launch B reaches the CoroutineExceptionHandler — it is NOT propagated to the supervisor (B was a direct child of supervisorScope)"),
            narrative(1900, "SupervisorJob does not see the failure — branches A and C are safe"),
            completing(2200, "Async A1 completes work", "a1"),
            completed(2300, "Async A1 completed", "a1"),
            completing(2400, "Async A2 completes work", "a2"),
            completed(2500, "Async A2 completed", "a2"),
            completing(2600, "Launch A completing", "branch-a"),
            completed(2700, "Launch A completed", "branch-a"),
            completing(2800, "Launch C completing", "branch-c"),
            completed(2900, "Launch C completed", "branch-c"),
            completing(3000, "Supervisor scope completing", "supervisor"),
            completed(3100, "Supervisor scope completed", "supervisor"),
            completing(3200, "Outer coroutineScope completing", "root"),
            completed(3300, "Outer coroutineScope completed", "root"),
            narrative(3400, "SupervisorJob at level 2 contained the failure — siblings completed normally")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    supervisorScope {
        launch {                        // branch A
            val a1 = async { "result1" }
            val a2 = async { "result2" }
            println(a1.await() + a2.await())
        }

        launch {                        // branch B (fails)
            launch {                    // B1
                throw IOException("Network error")
            }
        }

        launch {                        // branch C
            delay(1000)
            println("C completed")      // still completes!
        }
    }
    // supervisorScope contains B's failure — A and C finish
}
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()

        "beginner" -> {
            val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
                supervisorNode("supervisor", "supervisorScope", BuilderType.SupervisorScope,
                    node("child-a", "launch A", BuilderType.Launch),
                    node("child-b", "launch B (fails)", BuilderType.Launch)
                )
            )

            val events = listOf(
                narrative(0, "Simple nesting: coroutineScope > supervisorScope > 2 children"),
                starts(100, "Outer coroutineScope starts", "root"),
                starts(300, "supervisorScope starts", "supervisor"),
                starts(500, "Launch A starts", "child-a"),
                starts(700, "Launch B starts", "child-b"),
                narrative(1000, "Launch B throws an exception"),
                cancelling(1100, "Launch B enters Cancelling", "child-b"),
                cancelled(1200, "Launch B cancelled", "child-b"),
                narrative(1400, "Exception from B reaches the CoroutineExceptionHandler — it is NOT propagated to the supervisor (B was a direct child of supervisorScope)"),
                narrative(1500, "SupervisorJob does not see the failure — child A is unaffected"),
                completing(1800, "Launch A completing", "child-a"),
                completed(1900, "Launch A completed", "child-a"),
                completing(2100, "supervisorScope completing", "supervisor"),
                completed(2200, "supervisorScope completed", "supervisor"),
                completing(2400, "Outer coroutineScope completing", "root"),
                completed(2500, "Outer coroutineScope completed", "root"),
                narrative(2600, "SupervisorJob contained the failure — the surviving child completed normally")
            )

            timeline(
                tree = tree,
                events = events,
                kotlinCode = """
suspend fun main() = coroutineScope {
    supervisorScope {
        launch {                    // child A
            delay(1000)
            println("A completed")  // survives!
        }

        launch {                    // child B (fails)
            throw IOException("Network error")
        }
    }
}
                """.trimIndent()
            )
        }

        "advanced" -> {
            val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
                supervisorNode("supervisor", "supervisorScope", BuilderType.SupervisorScope,
                    node("branch-a", "launch A", BuilderType.Launch,
                        node("scope-a", "coroutineScope", BuilderType.CoroutineScope,
                            node("a1", "async A1", BuilderType.Async),
                            node("a2", "async A2", BuilderType.Async)
                        )
                    ),
                    node("branch-b", "launch B (fails)", BuilderType.Launch,
                        node("b1", "launch B1", BuilderType.Launch)
                    ),
                    node("branch-c", "launch C", BuilderType.Launch,
                        node("c1", "launch C1", BuilderType.Launch)
                    )
                )
            )

            val events = listOf(
                narrative(0, "5-level tree: coroutineScope > supervisorScope > 3 branches with mixed scope types"),
                starts(100, "Outer coroutineScope starts", "root"),
                starts(200, "supervisorScope starts", "supervisor"),
                starts(350, "Launch A starts", "branch-a"),
                starts(450, "Launch B starts", "branch-b"),
                starts(550, "Launch C starts", "branch-c"),
                starts(650, "coroutineScope starts inside A", "scope-a"),
                starts(750, "Async A1 starts", "a1"),
                starts(850, "Async A2 starts", "a2"),
                starts(950, "Launch B1 starts", "b1"),
                starts(1050, "Launch C1 starts", "c1"),
                narrative(1200, "B1 encounters a fault — exception propagates up through branch B"),
                cancelling(1300, "B1 fails", "b1"),
                cancelled(1400, "B1 cancelled", "b1"),
                exception(1500, "Exception from B1 propagates to launch B", "b1", "branch-b", "IOException"),
                cancelling(1600, "Launch B enters Cancelling", "branch-b"),
                cancelled(1700, "Launch B cancelled", "branch-b"),
                narrative(1800, "Exception from launch B reaches the CoroutineExceptionHandler — it is NOT propagated to the supervisor (B was a direct child of supervisorScope)"),
                narrative(1900, "SupervisorJob does not see the failure — branches A and C continue unaffected"),
                completing(2100, "Async A1 completes work", "a1"),
                completed(2200, "Async A1 completed", "a1"),
                completing(2300, "Async A2 completes work", "a2"),
                completed(2400, "Async A2 completed", "a2"),
                completing(2500, "coroutineScope completing", "scope-a"),
                completed(2600, "coroutineScope completed", "scope-a"),
                completing(2700, "Launch A completing", "branch-a"),
                completed(2800, "Launch A completed", "branch-a"),
                completing(2900, "Launch C1 completing", "c1"),
                completed(3000, "Launch C1 completed", "c1"),
                completing(3100, "Launch C completing", "branch-c"),
                completed(3200, "Launch C completed", "branch-c"),
                completing(3300, "supervisorScope completing", "supervisor"),
                completed(3400, "supervisorScope completed", "supervisor"),
                completing(3500, "Outer coroutineScope completing", "root"),
                completed(3600, "Outer coroutineScope completed", "root"),
                narrative(3700, "Multiple fault boundaries: coroutineScope groups A's children, supervisorScope isolates B's failure from A and C")
            )

            timeline(
                tree = tree,
                events = events,
                kotlinCode = """
suspend fun main() = coroutineScope {
    supervisorScope {
        launch {                                // branch A
            coroutineScope {                    // groups A's children
                val a1 = async { "result1" }
                val a2 = async { "result2" }
                println(a1.await() + a2.await())
            }
        }

        launch {                                // branch B (fails)
            launch {                            // B1
                throw IOException("Network error")
            }
        }

        launch {                                // branch C
            launch {                            // C1
                delay(500)
                println("C1 done")
            }
            println("C completed")
        }
    }
    // supervisorScope isolates B's failure
    // coroutineScope inside A groups its async children
}
                """.trimIndent()
            )
        }

        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }
}
