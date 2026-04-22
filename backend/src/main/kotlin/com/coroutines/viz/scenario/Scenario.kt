package com.coroutines.viz.scenario

import com.coroutines.viz.event.EventTimeline
import kotlinx.serialization.Serializable

@Serializable
data class ScenarioInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String
)

interface Scenario {
    val info: ScenarioInfo
    fun buildTimeline(): EventTimeline
    fun buildTimeline(level: String): EventTimeline = when (level) {
        "beginner", "intermediate", "advanced" -> buildTimeline()
        else -> buildTimeline()
    }
}
