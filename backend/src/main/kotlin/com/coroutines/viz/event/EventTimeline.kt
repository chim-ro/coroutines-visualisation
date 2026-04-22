package com.coroutines.viz.event

import com.coroutines.viz.model.CoroutineNode
import kotlinx.serialization.Serializable

@Serializable
data class ThreadSegment(
    val taskId: String,
    val taskName: String,
    val startMs: Long,
    val endMs: Long,
    val state: String  // "active", "blocked", "suspended"
)

@Serializable
data class ThreadLane(
    val threadName: String,
    val segments: List<ThreadSegment>
)

@Serializable
data class EventTimeline(
    val scenarioName: String,
    val tree: CoroutineNode,
    val secondTree: CoroutineNode? = null,
    val events: List<SimulationEvent>,
    val kotlinCode: String = "",
    val visualizationMode: String = "tree",
    val leftThreadLanes: List<ThreadLane>? = null,
    val rightThreadLanes: List<ThreadLane>? = null,
    val totalDurationMs: Long = 0
)
