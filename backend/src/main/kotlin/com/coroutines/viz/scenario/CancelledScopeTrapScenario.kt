package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

class CancelledScopeTrapScenario : Scenario {
    override val info = ScenarioInfo(
        id = "cancelled-scope-trap",
        name = "Cancelled Scope: Silent Drops",
        description = "Once a scope's Job is Cancelled, calling launch on it silently does nothing — the new coroutine is born already Cancelled, body never runs. A real bug-source in long-lived scopes (Android ViewModelScope, custom CoroutineScope(Job())) when one child failure cancels the whole scope.",
        category = "Cancellation"
    )

    override fun buildTimeline(): EventTimeline = buildIntermediateTimeline()

    override fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner" -> buildBeginnerTimeline()
        "intermediate" -> buildIntermediateTimeline()
        "advanced" -> buildAdvancedTimeline()
        else -> throw IllegalArgumentException("Unknown level '$level'. Must be one of: beginner, intermediate, advanced")
    }

    // ── Beginner: explicit cancel, then a ghost launch ───────────────────
    private fun buildBeginnerTimeline(): EventTimeline {
        val tree = node("scope", "CoroutineScope(Job())", BuilderType.CoroutineScope,
            node("first", "launch #1 (real)", BuilderType.Launch),
            node("ghost", "launch #2 (attempted AFTER cancel)", BuilderType.Launch)
        )

        val events = listOf(
            NarrativeEvent(0, "A manually-built scope: CoroutineScope(Job()). Watch what happens when we launch into it AFTER it's been cancelled."),
            StateChangeEvent(100, "Scope becomes Active", "scope", JobState.New, JobState.Active),
            StateChangeEvent(300, "launch #1 starts (scope is healthy)", "first", JobState.New, JobState.Active),
            StateChangeEvent(700, "launch #1 finishes", "first", JobState.Active, JobState.Completing),
            StateChangeEvent(800, "launch #1 completed", "first", JobState.Completing, JobState.Completed),
            NarrativeEvent(1000, "Now we explicitly cancel the scope (e.g., user leaves the screen, app shuts down)."),
            StateChangeEvent(1200, "scope.cancel() — scope enters Cancelling", "scope", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1400, "Scope Cancelled — its Job is in a terminal state and cannot accept new children", "scope", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(1700, "Some later code calls scope.launch { ... } again — maybe a delayed handler, maybe a button click. Watch the 'ghost' child."),
            StateChangeEvent(1900, "Ghost launch attempted: New → Cancelled IMMEDIATELY. The body never runs, no exception is thrown.", "ghost", JobState.New, JobState.Cancelled),
            NarrativeEvent(2100, "That's the trap: launch returned a Job, but it's already Cancelled. The println / API call / database write you wrote in the body — none of it happens. Silently."),
            NarrativeEvent(2500, "Mitigations: (1) check scope.isActive before launching, (2) use a SupervisorJob in the scope so child failures don't kill it, (3) recreate the scope when needed, (4) prefer structured coroutineScope { } over long-lived custom scopes when you can.")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
val scope = CoroutineScope(Job())

scope.launch {              // #1 — real work
    delay(200)
    println("#1 done")
}

delay(500)
scope.cancel()              // scope's Job is now Cancelled

delay(200)
scope.launch {              // #2 — SILENTLY DROPPED
    println("#2 done")      // NEVER RUNS, no exception
}
            """.trimIndent()
        )
    }

    // ── Intermediate: scope killed by child failure (Job, not SupervisorJob) ──
    private fun buildIntermediateTimeline(): EventTimeline {
        val tree = node("scope", "CoroutineScope(Job())", BuilderType.CoroutineScope,
            node("action-1", "launch #1", BuilderType.Launch),
            node("action-fails", "launch #2 (fails)", BuilderType.Launch),
            node("action-3-ghost", "launch #3 (ghost)", BuilderType.Launch),
            node("action-4-ghost", "launch #4 (ghost)", BuilderType.Launch)
        )

        val events = listOf(
            NarrativeEvent(0, "Think of this as a ViewModel scope. The user performs actions and each one launches a coroutine. One action's coroutine has a bug — and the whole scope dies silently."),
            StateChangeEvent(100, "Scope becomes Active — ready to handle actions", "scope", JobState.New, JobState.Active),
            StateChangeEvent(300, "User action #1 → launch starts", "action-1", JobState.New, JobState.Active),
            StateChangeEvent(700, "User action #2 → launch starts", "action-fails", JobState.New, JobState.Active),
            StateChangeEvent(900, "Action #1 completes successfully", "action-1", JobState.Active, JobState.Completing),
            StateChangeEvent(1000, "Action #1 Completed", "action-1", JobState.Completing, JobState.Completed),
            NarrativeEvent(1200, "Action #2 throws an uncaught exception (a bug — say, a NullPointerException)."),
            StateChangeEvent(1400, "Action #2 → Cancelling (exception)", "action-fails", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1500, "Action #2 → Cancelled", "action-fails", JobState.Cancelling, JobState.Cancelled),
            ExceptionEvent(1600, "Exception propagates UP from action #2 to the scope (it's a normal Job, not a SupervisorJob)", "action-fails", "scope", "NullPointerException"),
            StateChangeEvent(1700, "Scope → Cancelling (received child failure)", "scope", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1800, "Scope Cancelled — the trap is now armed", "scope", JobState.Cancelling, JobState.Cancelled),
            NarrativeEvent(2000, "The user is still using the app. They tap a button → action #3. Then another → action #4. Both call scope.launch."),
            StateChangeEvent(2300, "Action #3 launch attempted: New → Cancelled (silently dropped)", "action-3-ghost", JobState.New, JobState.Cancelled),
            StateChangeEvent(2500, "Action #4 launch attempted: New → Cancelled (silently dropped)", "action-4-ghost", JobState.New, JobState.Cancelled),
            NarrativeEvent(2800, "Both later actions did nothing. No exception, no log, no crash — just silent failure. The UI looks 'broken' to the user."),
            NarrativeEvent(3300, "Fix: use CoroutineScope(SupervisorJob()) — then action #2's failure stays isolated and the scope stays Active for actions #3 and #4. (See the Advanced level for a side-by-side.)")
        )

        return timeline(
            tree = tree,
            events = events,
            kotlinCode = """
class MyViewModel {
    private val scope = CoroutineScope(Job())   // ⚠️ Job() — vulnerable

    fun onAction1() {
        scope.launch { delay(100); println("#1") }
    }

    fun onAction2() {
        scope.launch {
            delay(200)
            throw NullPointerException("bug!")  // cancels scope
        }
    }

    // These run AFTER action #2 has cancelled the scope:
    fun onAction3() {
        scope.launch { println("#3") }          // SILENTLY DROPPED
    }
    fun onAction4() {
        scope.launch { println("#4") }          // SILENTLY DROPPED
    }
}
            """.trimIndent()
        )
    }

    // ── Advanced: side-by-side — Job() (vulnerable) vs SupervisorJob() (resilient) ──
    private fun buildAdvancedTimeline(): EventTimeline {
        // LEFT — CoroutineScope(Job()) — vulnerable to silent drops
        val left = node("j-scope", "CoroutineScope(Job())", BuilderType.CoroutineScope,
            node("j-1", "launch #1", BuilderType.Launch),
            node("j-2", "launch #2 (fails)", BuilderType.Launch),
            node("j-3", "launch #3 (after failure)", BuilderType.Launch)
        )

        // RIGHT — CoroutineScope(SupervisorJob()) — resilient.
        // Note: this is the CoroutineScope class with a SupervisorJob context element,
        // NOT the supervisorScope { } builder. We model it as BuilderType.CoroutineScope
        // (the factory function) + JobType.SupervisorJob (the internal Job), matching
        // the convention in ExternalScopeScenario.
        val right = supervisorNode("s-scope", "CoroutineScope(SupervisorJob())", BuilderType.CoroutineScope,
            node("s-1", "launch #1", BuilderType.Launch),
            node("s-2", "launch #2 (fails)", BuilderType.Launch),
            node("s-3", "launch #3 (after failure)", BuilderType.Launch)
        )

        val events = listOf(
            NarrativeEvent(0, "Same usage pattern, one difference: LEFT uses Job(), RIGHT uses SupervisorJob() in the scope's constructor. Watch what happens to #3."),
            // Both scopes start
            StateChangeEvent(100, "LEFT: scope Active", "j-scope", JobState.New, JobState.Active),
            StateChangeEvent(150, "RIGHT: scope Active", "s-scope", JobState.New, JobState.Active),
            // First action on both
            StateChangeEvent(300, "LEFT: #1 starts", "j-1", JobState.New, JobState.Active),
            StateChangeEvent(350, "RIGHT: #1 starts", "s-1", JobState.New, JobState.Active),
            // Second action (failing) on both
            StateChangeEvent(500, "LEFT: #2 starts", "j-2", JobState.New, JobState.Active),
            StateChangeEvent(550, "RIGHT: #2 starts", "s-2", JobState.New, JobState.Active),
            // First action completes on both
            StateChangeEvent(800, "LEFT: #1 completes", "j-1", JobState.Active, JobState.Completing),
            StateChangeEvent(850, "RIGHT: #1 completes", "s-1", JobState.Active, JobState.Completing),
            StateChangeEvent(900, "LEFT: #1 Completed", "j-1", JobState.Completing, JobState.Completed),
            StateChangeEvent(950, "RIGHT: #1 Completed", "s-1", JobState.Completing, JobState.Completed),
            // Second action fails on both
            NarrativeEvent(1100, "Both #2 children throw the same exception..."),
            StateChangeEvent(1300, "LEFT: #2 → Cancelling", "j-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1350, "RIGHT: #2 → Cancelling", "s-2", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1400, "LEFT: #2 Cancelled", "j-2", JobState.Cancelling, JobState.Cancelled),
            StateChangeEvent(1450, "RIGHT: #2 Cancelled", "s-2", JobState.Cancelling, JobState.Cancelled),
            // LEFT: exception propagates up, scope dies
            ExceptionEvent(1600, "LEFT: exception propagates UP to scope (it's a Job, not SupervisorJob)", "j-2", "j-scope", "RuntimeException"),
            StateChangeEvent(1700, "LEFT: scope → Cancelling", "j-scope", JobState.Active, JobState.Cancelling),
            StateChangeEvent(1800, "LEFT: scope Cancelled (TRAP ARMED — future launches will be silently dropped)", "j-scope", JobState.Cancelling, JobState.Cancelled),
            // RIGHT: exception goes to handler, scope unaffected
            NarrativeEvent(1900, "RIGHT: #2's failure goes to the CoroutineExceptionHandler — the supervisor scope is unaffected."),
            // Now both attempt #3
            NarrativeEvent(2200, "Now both sides attempt scope.launch { } again — same call, different outcomes."),
            // LEFT: #3 silently dropped
            StateChangeEvent(2400, "LEFT: #3 New → Cancelled IMMEDIATELY (silent drop)", "j-3", JobState.New, JobState.Cancelled),
            // RIGHT: #3 runs normally
            StateChangeEvent(2450, "RIGHT: #3 New → Active (scope is still alive)", "s-3", JobState.New, JobState.Active),
            StateChangeEvent(2800, "RIGHT: #3 completes", "s-3", JobState.Active, JobState.Completing),
            StateChangeEvent(2900, "RIGHT: #3 Completed", "s-3", JobState.Completing, JobState.Completed),
            NarrativeEvent(3100, "RIGHT: scope is still Active and useful — children come and go, failures don't poison it."),
            // RIGHT scope eventually completes (children all done)
            // (Note: in real code with a long-lived custom scope, the scope wouldn't auto-complete — but for visualization we show it can.)
            NarrativeEvent(3500, "Rule of thumb: for long-lived application/ViewModel scopes, ALWAYS use CoroutineScope(SupervisorJob() + ...). One badly-thrown exception in a child should not poison the entire scope.")
        )

        return timeline(
            tree = left,
            secondTree = right,
            events = events,
            kotlinCode = """
// LEFT — vulnerable: one child failure kills the whole scope
val left = CoroutineScope(Job())

left.launch { delay(200); println("L #1 done") }
left.launch { delay(300); throw RuntimeException("boom") }
// ...later...
left.launch { println("L #3 done") }   // ⚠️ SILENTLY DROPPED


// RIGHT — resilient: child failures are isolated
val right = CoroutineScope(SupervisorJob())

right.launch { delay(200); println("R #1 done") }
right.launch { delay(300); throw RuntimeException("boom") }
// ...later...
right.launch { println("R #3 done") }  // ✅ RUNS
            """.trimIndent()
        )
    }
}
