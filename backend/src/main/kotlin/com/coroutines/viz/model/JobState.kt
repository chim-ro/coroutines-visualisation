package com.coroutines.viz.model

import kotlinx.serialization.Serializable

@Serializable
enum class JobState {
    New, Active, Suspended, Completing, Completed, Cancelling, Cancelled
}
