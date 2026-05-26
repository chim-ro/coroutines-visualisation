package com.coroutines.viz.scenario

import com.coroutines.viz.scenario.TimelineTestHelper.assertTimelineIsValid
import kotlin.test.*

class ScenarioRegistryTest {

    @Test
    fun `registry contains all 19 scenarios`() {
        val scenarios = ScenarioRegistry.listAll()
        assertEquals(19, scenarios.size, "Should have 19 registered scenarios")
    }

    @Test
    fun `all scenario IDs are unique`() {
        val scenarios = ScenarioRegistry.listAll()
        val ids = scenarios.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "All scenario IDs should be unique")
    }

    @Test
    fun `all scenarios can build timelines for all 3 levels without exceptions`() {
        val levels = listOf("beginner", "intermediate", "advanced")
        val scenarios = ScenarioRegistry.listAll()

        for (info in scenarios) {
            val scenario = ScenarioRegistry.getById(info.id)
            assertNotNull(scenario, "Scenario '${info.id}' should be retrievable by ID")

            for (level in levels) {
                val timeline = scenario.buildTimeline(level)
                assertTimelineIsValid(timeline)
                assertTrue(timeline.events.isNotEmpty(),
                    "Scenario '${info.id}' at level '$level' should have events")
                assertTrue(timeline.scenarioName.isNotBlank(),
                    "Scenario '${info.id}' should have a non-blank name")
            }
        }
    }

    @Test
    fun `getById returns null for unknown scenario`() {
        assertNull(ScenarioRegistry.getById("nonexistent"))
    }

    @Test
    fun `all scenarios have non-empty categories`() {
        for (info in ScenarioRegistry.listAll()) {
            assertTrue(info.category.isNotBlank(), "Scenario '${info.id}' should have a category")
            assertTrue(info.description.isNotBlank(), "Scenario '${info.id}' should have a description")
        }
    }

    /**
     * BUG: The Scenario interface default buildTimeline(level) returns the same
     * result for ALL levels. It maps "beginner", "intermediate", "advanced" all
     * to buildTimeline(). Subclasses must override to differentiate levels.
     */
    @Test
    fun `all scenarios return different timelines for different levels`() {
        val levels = listOf("beginner", "intermediate", "advanced")

        for (info in ScenarioRegistry.listAll()) {
            val scenario = ScenarioRegistry.getById(info.id)!!
            val timelines = levels.map { scenario.buildTimeline(it) }

            // At minimum, different levels should have different event counts or tree sizes
            val eventCounts = timelines.map { it.events.size }
            val treeSizes = timelines.map { TimelineTestHelper.collectNodeIds(it.tree).size }

            // At least some levels should differ (not all identical)
            val allSameEvents = eventCounts.distinct().size == 1
            val allSameTrees = treeSizes.distinct().size == 1
            assertFalse(allSameEvents && allSameTrees,
                "BUG: Scenario '${info.id}' returns identical timelines for all levels. " +
                        "Events: $eventCounts, Tree sizes: $treeSizes. " +
                        "The default buildTimeline(level) in Scenario interface ignores the level parameter.")
        }
    }

    @Test
    fun `unknown level throws IllegalArgumentException`() {
        val scenario = ScenarioRegistry.getById("happy-path")!!
        assertFailsWith<IllegalArgumentException> {
            scenario.buildTimeline("expert")
        }
    }
}
