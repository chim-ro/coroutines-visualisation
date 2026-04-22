package com.coroutines.viz.model

import kotlinx.serialization.Serializable

@Serializable
data class CoroutineNode(
    val id: String,
    val displayName: String,
    val builder: BuilderType,
    val jobType: JobType,
    val initialState: JobState,
    val children: List<CoroutineNode> = emptyList()
)

