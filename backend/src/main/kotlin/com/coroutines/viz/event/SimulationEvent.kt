package com.coroutines.viz.event

import com.coroutines.viz.model.JobState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SimulationEvent {
    abstract val delayMs: Long
    abstract val description: String
}

@Serializable
@SerialName("stateChange")
data class StateChangeEvent(
    override val delayMs: Long,
    override val description: String,
    val nodeId: String,
    val fromState: JobState,
    val toState: JobState
) : SimulationEvent()

@Serializable
@SerialName("cancellation")
data class CancellationEvent(
    override val delayMs: Long,
    override val description: String,
    val sourceNodeId: String,
    val targetNodeId: String
) : SimulationEvent()

@Serializable
@SerialName("exception")
data class ExceptionEvent(
    override val delayMs: Long,
    override val description: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val exceptionMessage: String
) : SimulationEvent()

@Serializable
@SerialName("narrative")
data class NarrativeEvent(
    override val delayMs: Long,
    override val description: String
) : SimulationEvent()
