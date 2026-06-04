package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*

/**
 * Small DSL for building scenario data with less boilerplate.
 *
 * Every coroutine node in a scenario starts in [JobState.New], so [node] and
 * [supervisorNode] hardcode that. Children are passed as varargs, which reads
 * naturally for both leaf nodes and deeply nested trees.
 */

/** A coroutine node backed by a regular [JobType.Job]. Children are given as varargs. */
fun node(
    id: String,
    displayName: String,
    builder: BuilderType,
    vararg children: CoroutineNode
): CoroutineNode = CoroutineNode(
    id = id,
    displayName = displayName,
    builder = builder,
    jobType = JobType.Job,
    initialState = JobState.New,
    children = children.toList()
)

/** A coroutine node backed by a [JobType.SupervisorJob] (e.g. supervisorScope / SupervisorJob()). */
fun supervisorNode(
    id: String,
    displayName: String,
    builder: BuilderType,
    vararg children: CoroutineNode
): CoroutineNode = CoroutineNode(
    id = id,
    displayName = displayName,
    builder = builder,
    jobType = JobType.SupervisorJob,
    initialState = JobState.New,
    children = children.toList()
)

// --- Event verbs -----------------------------------------------------------
// Each verb is a thin alias for a SimulationEvent whose state transition is
// fixed by the verb's meaning, so call sites carry only the bespoke parts
// (time, description, node) and never repeat the JobState pair.

/** A node activating for the first time: New → Active. */
fun starts(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.New, JobState.Active)

/** A node finishing its work and entering the completing handshake: Active → Completing. */
fun completing(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Active, JobState.Completing)

/** A node fully completed: Completing → Completed. */
fun completed(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Completing, JobState.Completed)

/** A node beginning cancellation: Active → Cancelling. */
fun cancelling(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Active, JobState.Cancelling)

/** A node fully cancelled: Cancelling → Cancelled. */
fun cancelled(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Cancelling, JobState.Cancelled)

/** A node suspending at a suspension point: Active → Suspended. */
fun suspends(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Active, JobState.Suspended)

/** A suspended node resuming: Suspended → Active. */
fun resumes(at: Long, description: String, nodeId: String): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, JobState.Suspended, JobState.Active)

/** Escape hatch for any state change the verbs above don't cover (e.g. New → Cancelled). */
fun transition(at: Long, description: String, nodeId: String, from: JobState, to: JobState): StateChangeEvent =
    StateChangeEvent(at, description, nodeId, from, to)

/** A pure narration step with no state change. */
fun narrative(at: Long, description: String): NarrativeEvent =
    NarrativeEvent(at, description)

/** A cancellation signal travelling from one node to another. */
fun cancellation(at: Long, description: String, sourceNodeId: String, targetNodeId: String): CancellationEvent =
    CancellationEvent(at, description, sourceNodeId, targetNodeId)

/** An exception propagating from one node to another. */
fun exception(at: Long, description: String, sourceNodeId: String, targetNodeId: String, exceptionMessage: String): ExceptionEvent =
    ExceptionEvent(at, description, sourceNodeId, targetNodeId, exceptionMessage)

/**
 * Build an [EventTimeline] for this scenario, defaulting [EventTimeline.scenarioName]
 * to the scenario's own name so call sites don't have to repeat `info.name`.
 */
fun Scenario.timeline(
    tree: CoroutineNode,
    events: List<SimulationEvent>,
    kotlinCode: String = "",
    secondTree: CoroutineNode? = null,
    visualizationMode: String = "tree",
    leftThreadLanes: List<ThreadLane>? = null,
    rightThreadLanes: List<ThreadLane>? = null,
    totalDurationMs: Long = 0
): EventTimeline = EventTimeline(
    scenarioName = info.name,
    tree = tree,
    secondTree = secondTree,
    events = events,
    kotlinCode = kotlinCode,
    visualizationMode = visualizationMode,
    leftThreadLanes = leftThreadLanes,
    rightThreadLanes = rightThreadLanes,
    totalDurationMs = totalDurationMs
)
