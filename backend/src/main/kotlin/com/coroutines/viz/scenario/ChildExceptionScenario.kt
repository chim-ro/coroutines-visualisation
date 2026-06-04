package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class ChildExceptionScenario : Scenario {
    override val info = ScenarioInfo(
        id = "child-exception",
        name = "Child Exception",
        description = "A child throws an exception — it propagates UP to the parent, which cancels siblings.",
        category = "Exceptions"
    )

    override fun buildTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1", BuilderType.Launch),
            node("child-2", "launch #2 (fails)", BuilderType.Launch),
            node("child-3", "async #3", BuilderType.Async)
        )

        val events = listOf(
            narrative(0, "coroutineScope starts with 3 children"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "launch #2 starts", "child-2"),
            starts(500, "async #3 starts", "child-3"),
            narrative(800, "launch #2 encounters an error and throws an exception!"),
            cancelling(1000, "launch #2 fails — enters Cancelling", "child-2"),
            cancelled(1100, "launch #2 is Cancelled", "child-2"),
            exception(1300, "Exception propagates UP from launch #2 to parent scope", "child-2", "root", "RuntimeException: Something went wrong"),
            narrative(1400, "Parent receives exception — must cancel all other children"),
            cancelling(1500, "Parent scope enters Cancelling", "root"),
            cancellation(1600, "Parent cancels launch #1", "root", "child-1"),
            cancelling(1700, "launch #1 enters Cancelling", "child-1"),
            cancellation(1800, "Parent cancels async #3", "root", "child-3"),
            cancelling(1900, "async #3 enters Cancelling", "child-3"),
            cancelled(2100, "launch #1 cancelled", "child-1"),
            cancelled(2200, "async #3 cancelled", "child-3"),
            cancelled(2400, "Parent scope cancelled — exception re-thrown", "root"),
            narrative(2500, "Exceptions propagate UPWARD, then cancellation flows DOWNWARD to siblings")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {  // launch #1
        delay(2000)
        println("Child 1 done")
    }

    launch {  // launch #2 (fails)
        delay(500)
        throw RuntimeException("Something went wrong")
    }

    async {   // async #3
        delay(2000)
        "result"
    }
    // Exception from #2 cancels #1 and #3, then re-throws
}
            """.trimIndent()
        )
    }

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "intermediate" -> buildTimeline()
        "beginner" -> buildBeginnerTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child", "launch (fails)", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "coroutineScope starts with 1 child"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch starts", "child"),
            narrative(600, "The child coroutine encounters an error and throws an exception!"),
            cancelling(800, "launch fails — enters Cancelling", "child"),
            cancelled(900, "launch is Cancelled", "child"),
            exception(1100, "Exception propagates UP from child to parent scope", "child", "root", "RuntimeException: Something went wrong"),
            cancelling(1300, "Parent scope enters Cancelling", "root"),
            cancelled(1500, "Parent scope cancelled — exception re-thrown", "root"),
            narrative(1600, "The exception propagated from child to parent, cancelling the entire scope")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {
        delay(500)
        throw RuntimeException("Something went wrong")
    }
    // Exception propagates up and cancels the scope
}
            """.trimIndent()
        )
    }

    private fun buildAdvancedTimeline(): EventTimeline {
        val tree = node("root", "coroutineScope", BuilderType.CoroutineScope,
            node("child-1", "launch #1", BuilderType.Launch),
            node("child-2", "launch #2", BuilderType.Launch,
                node("grandchild", "launch (fails)", BuilderType.Launch)
            ),
            node("child-3", "async #3", BuilderType.Async),
            node("child-4", "launch #4", BuilderType.Launch)
        )

        val events = listOf(
            narrative(0, "coroutineScope starts with 4 children; child-2 has a grandchild"),
            starts(100, "Scope becomes Active", "root"),
            starts(300, "launch #1 starts", "child-1"),
            starts(400, "launch #2 starts", "child-2"),
            starts(500, "async #3 starts", "child-3"),
            starts(600, "launch #4 starts", "child-4"),
            starts(700, "grandchild starts inside launch #2", "grandchild"),
            narrative(1000, "The grandchild coroutine encounters an error and throws!"),
            cancelling(1200, "grandchild fails — enters Cancelling", "grandchild"),
            cancelled(1300, "grandchild is Cancelled", "grandchild"),
            exception(1500, "Exception propagates UP from grandchild to launch #2", "grandchild", "child-2", "RuntimeException: Deep failure"),
            cancelling(1700, "launch #2 enters Cancelling", "child-2"),
            cancelled(1800, "launch #2 is Cancelled", "child-2"),
            exception(2000, "Exception propagates UP from launch #2 to parent scope", "child-2", "root", "RuntimeException: Deep failure"),
            narrative(2100, "Parent receives exception — must cancel all remaining children"),
            cancelling(2200, "Parent scope enters Cancelling", "root"),
            cancellation(2300, "Parent cancels launch #1", "root", "child-1"),
            cancelling(2400, "launch #1 enters Cancelling", "child-1"),
            cancellation(2500, "Parent cancels async #3", "root", "child-3"),
            cancelling(2600, "async #3 enters Cancelling", "child-3"),
            cancellation(2700, "Parent cancels launch #4", "root", "child-4"),
            cancelling(2800, "launch #4 enters Cancelling", "child-4"),
            cancelled(3000, "launch #1 cancelled", "child-1"),
            cancelled(3100, "async #3 cancelled", "child-3"),
            cancelled(3200, "launch #4 cancelled", "child-4"),
            cancelled(3400, "Parent scope cancelled — exception re-thrown", "root"),
            narrative(3500, "Exception propagated from grandchild → child-2 → root, then cancellation flowed down to all siblings")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
suspend fun main() = coroutineScope {
    launch {  // launch #1
        delay(5000)
        println("Child 1 done")
    }

    launch {  // launch #2
        launch {  // grandchild (fails)
            delay(800)
            throw RuntimeException("Deep failure")
        }
        delay(5000)
        println("Child 2 done")
    }

    async {   // async #3
        delay(5000)
        "result"
    }

    launch {  // launch #4
        delay(5000)
        println("Child 4 done")
    }
    // Grandchild exception propagates to #2, then to root,
    // which cancels #1, #3, and #4 before re-throwing
}
            """.trimIndent()
        )
    }
}
