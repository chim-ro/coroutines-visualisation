package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ScopeComparisonScenario : Scenario {
    override val info = ScenarioInfo(
        id = "scope-comparison",
        name = "Scope Comparison",
        description = "Side-by-side: coroutineScope vs supervisorScope with the same exception.",
        category = "Comparison"
    )

    override fun buildTimeline(): EventTimeline {
        // Left tree: coroutineScope
        val tree1 = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-child-1", "launch #1", BuilderType.Launch),
            node("cs-child-2", "launch #2 (fails)", BuilderType.Launch),
            node("cs-child-3", "launch #3", BuilderType.Launch)
        )

        // Right tree: supervisorScope
        val tree2 = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-child-1", "launch #1", BuilderType.Launch),
            node("ss-child-2", "launch #2 (fails)", BuilderType.Launch),
            node("ss-child-3", "launch #3", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two identical structures — but different scope types"),
            // Both scopes start
            starts(100, "coroutineScope starts", "cs-root"),
            starts(150, "supervisorScope starts", "ss-root"),
            // All children start
            starts(300, "CS: launch #1 starts", "cs-child-1"),
            starts(350, "SS: launch #1 starts", "ss-child-1"),
            starts(400, "CS: launch #2 starts", "cs-child-2"),
            starts(450, "SS: launch #2 starts", "ss-child-2"),
            starts(500, "CS: launch #3 starts", "cs-child-3"),
            starts(550, "SS: launch #3 starts", "ss-child-3"),
            narrative(800, "Both launch #2 throw the same exception..."),
            // Both #2 fail
            cancelling(1000, "CS: launch #2 fails", "cs-child-2"),
            cancelling(1050, "SS: launch #2 fails", "ss-child-2"),
            cancelled(1100, "CS: launch #2 cancelled", "cs-child-2"),
            cancelled(1150, "SS: launch #2 cancelled", "ss-child-2"),
            // Exceptions propagate
            exception(1300, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            narrative(1350, "SS: exception goes to the CoroutineExceptionHandler — NOT propagated to the supervisor"),
            // coroutineScope cancels siblings
            narrative(1500, "coroutineScope cancels all siblings — supervisorScope's handler just logs and moves on"),
            cancelling(1600, "CS: scope enters Cancelling", "cs-root"),
            cancellation(1700, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            cancelling(1800, "CS: launch #1 cancelling", "cs-child-1"),
            cancellation(1850, "CS: cancelling launch #3", "cs-root", "cs-child-3"),
            cancelling(1900, "CS: launch #3 cancelling", "cs-child-3"),
            cancelled(2000, "CS: launch #1 cancelled", "cs-child-1"),
            cancelled(2100, "CS: launch #3 cancelled", "cs-child-3"),
            cancelled(2200, "CS: scope cancelled", "cs-root"),
            // supervisorScope siblings continue
            narrative(2300, "SS siblings continue normally..."),
            completing(2500, "SS: launch #1 completing", "ss-child-1"),
            completed(2600, "SS: launch #1 completed", "ss-child-1"),
            completing(2700, "SS: launch #3 completing", "ss-child-3"),
            completed(2800, "SS: launch #3 completed", "ss-child-3"),
            completing(2900, "SS: supervisor scope completing", "ss-root"),
            completed(3000, "SS: supervisor scope completed", "ss-root"),
            narrative(3100, "coroutineScope: all-or-nothing | supervisorScope: independent children")
        )

        return timeline(
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope — all-or-nothing
coroutineScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
    launch { delay(2000); println("#3 done") }
}
// Exception cancels ALL siblings, scope re-throws

// RIGHT: supervisorScope — independent children
supervisorScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
    launch { delay(2000); println("#3 done") }
}
// Only the failing child is cancelled, siblings complete
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree1 = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-child-1", "launch #1", BuilderType.Launch),
            node("cs-child-2", "launch #2 (fails)", BuilderType.Launch)
        )

        val tree2 = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-child-1", "launch #1", BuilderType.Launch),
            node("ss-child-2", "launch #2 (fails)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Two scopes, two children each — same exception, different behavior"),
            // Both scopes start
            starts(100, "coroutineScope starts", "cs-root"),
            starts(150, "supervisorScope starts", "ss-root"),
            // All children start
            starts(300, "CS: launch #1 starts", "cs-child-1"),
            starts(350, "SS: launch #1 starts", "ss-child-1"),
            starts(500, "CS: launch #2 starts", "cs-child-2"),
            starts(550, "SS: launch #2 starts", "ss-child-2"),
            narrative(800, "Both launch #2 throw an exception..."),
            // Both #2 fail
            cancelling(1000, "CS: launch #2 fails", "cs-child-2"),
            cancelling(1050, "SS: launch #2 fails", "ss-child-2"),
            cancelled(1100, "CS: launch #2 cancelled", "cs-child-2"),
            cancelled(1150, "SS: launch #2 cancelled", "ss-child-2"),
            // Exceptions propagate
            exception(1300, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            narrative(1350, "SS: exception goes to the CoroutineExceptionHandler — NOT propagated to the supervisor"),
            // coroutineScope cancels sibling
            narrative(1500, "coroutineScope cancels sibling — supervisorScope's handler just logs and moves on"),
            cancelling(1600, "CS: scope enters Cancelling", "cs-root"),
            cancellation(1700, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            cancelling(1800, "CS: launch #1 cancelling", "cs-child-1"),
            cancelled(1900, "CS: launch #1 cancelled", "cs-child-1"),
            cancelled(2000, "CS: scope cancelled", "cs-root"),
            // supervisorScope sibling continues
            narrative(2100, "SS: launch #1 continues normally..."),
            completing(2300, "SS: launch #1 completing", "ss-child-1"),
            completed(2400, "SS: launch #1 completed", "ss-child-1"),
            completing(2500, "SS: supervisor scope completing", "ss-root"),
            completed(2600, "SS: supervisor scope completed", "ss-root"),
            narrative(2700, "coroutineScope: cancelled everything | supervisorScope: only the failing child stopped")
        )

        return timeline(
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope
coroutineScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
}
// Exception cancels sibling, scope re-throws

// RIGHT: supervisorScope
supervisorScope {
    launch { delay(2000); println("#1 done") }
    launch { throw RuntimeException() }  // fails
}
// Only the failing child is cancelled
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree1 = node("cs-root", "coroutineScope", BuilderType.CoroutineScope,
            node("cs-child-1", "launch #1", BuilderType.Launch,
                node("cs-grandchild-1a", "launch #1a", BuilderType.Launch),
                node("cs-grandchild-1b", "launch #1b", BuilderType.Launch)
            ),
            node("cs-child-2", "launch #2 (fails)", BuilderType.Launch),
            node("cs-child-3", "launch #3", BuilderType.Launch)
        )

        val tree2 = supervisorNode("ss-root", "supervisorScope", BuilderType.SupervisorScope,
            node("ss-child-1", "launch #1", BuilderType.Launch,
                node("ss-grandchild-1a", "launch #1a", BuilderType.Launch),
                node("ss-grandchild-1b", "launch #1b", BuilderType.Launch)
            ),
            node("ss-child-2", "launch #2 (fails)", BuilderType.Launch),
            node("ss-child-3", "launch #3", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "Nested trees — exception propagation through multiple levels"),
            // Both scopes start
            starts(100, "coroutineScope starts", "cs-root"),
            starts(150, "supervisorScope starts", "ss-root"),
            // All children start
            starts(300, "CS: launch #1 starts", "cs-child-1"),
            starts(350, "SS: launch #1 starts", "ss-child-1"),
            starts(400, "CS: launch #2 starts", "cs-child-2"),
            starts(450, "SS: launch #2 starts", "ss-child-2"),
            starts(500, "CS: launch #3 starts", "cs-child-3"),
            starts(550, "SS: launch #3 starts", "ss-child-3"),
            // Grandchildren start
            starts(700, "CS: launch #1a starts", "cs-grandchild-1a"),
            starts(750, "SS: launch #1a starts", "ss-grandchild-1a"),
            starts(800, "CS: launch #1b starts", "cs-grandchild-1b"),
            starts(850, "SS: launch #1b starts", "ss-grandchild-1b"),
            narrative(1000, "All coroutines running — now launch #2 throws on both sides..."),
            // Both #2 fail
            cancelling(1200, "CS: launch #2 fails", "cs-child-2"),
            cancelling(1250, "SS: launch #2 fails", "ss-child-2"),
            cancelled(1300, "CS: launch #2 cancelled", "cs-child-2"),
            cancelled(1350, "SS: launch #2 cancelled", "ss-child-2"),
            // Exceptions propagate
            exception(1500, "CS: exception propagates to coroutineScope", "cs-child-2", "cs-root", "RuntimeException"),
            narrative(1550, "SS: exception goes to the CoroutineExceptionHandler — NOT propagated to the supervisor"),
            // coroutineScope cancels EVERYTHING — siblings and their children
            narrative(1700, "coroutineScope cancels the entire tree — including grandchildren!"),
            cancelling(1800, "CS: scope enters Cancelling", "cs-root"),
            cancellation(1900, "CS: cancelling launch #1", "cs-root", "cs-child-1"),
            cancelling(1950, "CS: launch #1 cancelling", "cs-child-1"),
            cancellation(2000, "CS: cancelling launch #1a", "cs-child-1", "cs-grandchild-1a"),
            cancelling(2050, "CS: launch #1a cancelling", "cs-grandchild-1a"),
            cancellation(2100, "CS: cancelling launch #1b", "cs-child-1", "cs-grandchild-1b"),
            cancelling(2150, "CS: launch #1b cancelling", "cs-grandchild-1b"),
            cancellation(2200, "CS: cancelling launch #3", "cs-root", "cs-child-3"),
            cancelling(2250, "CS: launch #3 cancelling", "cs-child-3"),
            cancelled(2400, "CS: launch #1a cancelled", "cs-grandchild-1a"),
            cancelled(2450, "CS: launch #1b cancelled", "cs-grandchild-1b"),
            cancelled(2500, "CS: launch #1 cancelled", "cs-child-1"),
            cancelled(2550, "CS: launch #3 cancelled", "cs-child-3"),
            cancelled(2650, "CS: scope cancelled", "cs-root"),
            // supervisorScope — siblings and grandchildren all continue
            narrative(2700, "SS: siblings and their grandchildren continue unaffected"),
            completing(2900, "SS: launch #1a completing", "ss-grandchild-1a"),
            completed(2950, "SS: launch #1a completed", "ss-grandchild-1a"),
            completing(3000, "SS: launch #1b completing", "ss-grandchild-1b"),
            completed(3050, "SS: launch #1b completed", "ss-grandchild-1b"),
            completing(3150, "SS: launch #1 completing", "ss-child-1"),
            completed(3200, "SS: launch #1 completed", "ss-child-1"),
            completing(3300, "SS: launch #3 completing", "ss-child-3"),
            completed(3350, "SS: launch #3 completed", "ss-child-3"),
            completing(3450, "SS: supervisor scope completing", "ss-root"),
            completed(3500, "SS: supervisor scope completed", "ss-root"),
            narrative(3600, "coroutineScope: entire tree destroyed | supervisorScope: only the failing child stopped, nested coroutines completed")
        )

        return timeline(
            tree = tree1,
            secondTree = tree2,
            events = events,
            kotlinCode = """
// LEFT: coroutineScope — cascading cancellation
coroutineScope {
    launch {                            // #1
        launch { delay(3000); println("#1a done") }
        launch { delay(3000); println("#1b done") }
        delay(3000); println("#1 done")
    }
    launch { throw RuntimeException() } // #2 — fails
    launch { delay(3000); println("#3 done") }
}
// Exception propagates up, cancels ALL children + grandchildren

// RIGHT: supervisorScope — isolated failure
supervisorScope {
    launch {                            // #1
        launch { delay(3000); println("#1a done") }
        launch { delay(3000); println("#1b done") }
        delay(3000); println("#1 done")
    }
    launch { throw RuntimeException() } // #2 — fails
    launch { delay(3000); println("#3 done") }
}
// Only #2 fails — #1, #1a, #1b, and #3 all complete
            """.trimIndent()
        )
    }
}
