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
