package com.coroutines.viz.model

import kotlinx.serialization.Serializable

@Serializable
enum class BuilderType {
    Launch, Async, CoroutineScope, SupervisorScope, RunBlocking
}
