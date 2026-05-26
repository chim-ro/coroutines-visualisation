package com.coroutines.viz.scenario

import com.coroutines.viz.event.*
import com.coroutines.viz.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Shared validation utilities for scenario timeline tests.
 */
object TimelineTestHelper {

    /** Collect all node IDs from a tree recursively. */
    fun collectNodeIds(node: CoroutineNode): Set<String> {
        val ids = mutableSetOf(node.id)
        for (child in node.children) {
            ids.addAll(collectNodeIds(child))
        }
        return ids
    }

    /** Verify that all node IDs referenced in events exist in the tree(s). */
    fun assertAllEventNodeIdsExistInTree(timeline: EventTimeline) {
        val treeIds = collectNodeIds(timeline.tree)
        val secondTreeIds = timeline.secondTree?.let { collectNodeIds(it) } ?: emptySet()
        val allIds = treeIds + secondTreeIds

        for (event in timeline.events) {
            when (event) {
                is StateChangeEvent -> {
                    assertTrue(
                        event.nodeId in allIds,
                        "StateChangeEvent references unknown nodeId '${event.nodeId}'. " +
                                "Available: $allIds. Description: '${event.description}'"
                    )
                }
                is CancellationEvent -> {
                    assertTrue(
                        event.sourceNodeId in allIds,
                        "CancellationEvent references unknown sourceNodeId '${event.sourceNodeId}'. " +
                                "Available: $allIds"
                    )
                    assertTrue(
                        event.targetNodeId in allIds,
                        "CancellationEvent references unknown targetNodeId '${event.targetNodeId}'. " +
                                "Available: $allIds"
                    )
                }
                is ExceptionEvent -> {
                    assertTrue(
                        event.sourceNodeId in allIds,
                        "ExceptionEvent references unknown sourceNodeId '${event.sourceNodeId}'. " +
                                "Available: $allIds"
                    )
                    assertTrue(
                        event.targetNodeId in allIds,
                        "ExceptionEvent references unknown targetNodeId '${event.targetNodeId}'. " +
                                "Available: $allIds"
                    )
                }
                is NarrativeEvent -> { /* no node references */ }
            }
        }
    }

    /** Verify that event delayMs values are non-decreasing (monotonic). */
    fun assertEventsAreChronological(timeline: EventTimeline) {
        val events = timeline.events
        for (i in 1 until events.size) {
            assertTrue(
                events[i].delayMs >= events[i - 1].delayMs,
                "Events are not chronological: event[$i] has delayMs=${events[i].delayMs} " +
                        "but event[${i - 1}] has delayMs=${events[i - 1].delayMs}. " +
                        "Event[$i]: '${events[i].description}'"
            )
        }
    }

    /** Verify that StateChangeEvents have valid transitions (fromState != toState). */
    fun assertNoNoopStateChanges(timeline: EventTimeline) {
        for (event in timeline.events) {
            if (event is StateChangeEvent) {
                assertTrue(
                    event.fromState != event.toState,
                    "No-op state change: '${event.nodeId}' transitions from ${event.fromState} " +
                            "to ${event.toState}. Description: '${event.description}'"
                )
            }
        }
    }

    /**
     * For each node, verify that state transitions are consistent:
     * the fromState of each subsequent event matches the toState of the previous event for that node.
     */
    fun assertStateTransitionConsistency(timeline: EventTimeline) {
        val lastState = mutableMapOf<String, JobState>()

        // Initialize all nodes from tree to their initial states
        fun initStates(node: CoroutineNode) {
            lastState[node.id] = node.initialState
            node.children.forEach { initStates(it) }
        }
        initStates(timeline.tree)
        timeline.secondTree?.let { initStates(it) }

        for (event in timeline.events) {
            if (event is StateChangeEvent) {
                val expected = lastState[event.nodeId]
                if (expected != null) {
                    assertEquals(
                        expected, event.fromState,
                        "State inconsistency for '${event.nodeId}': " +
                                "last known state is $expected but event fromState is ${event.fromState}. " +
                                "Description: '${event.description}'"
                    )
                }
                lastState[event.nodeId] = event.toState
            }
        }
    }

    /** Verify that all node IDs in the tree are unique. */
    fun assertUniqueNodeIds(timeline: EventTimeline) {
        fun collectAll(node: CoroutineNode, ids: MutableList<String>) {
            ids.add(node.id)
            node.children.forEach { collectAll(it, ids) }
        }
        val allIds = mutableListOf<String>()
        collectAll(timeline.tree, allIds)
        timeline.secondTree?.let { collectAll(it, allIds) }

        val duplicates = allIds.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Duplicate node IDs found: $duplicates")
    }

    /** Verify that the kotlinCode field is non-empty. */
    fun assertKotlinCodePresent(timeline: EventTimeline) {
        assertTrue(timeline.kotlinCode.isNotBlank(), "Kotlin code should not be blank")
    }

    /** Count events of a specific type. */
    inline fun <reified T : SimulationEvent> countEvents(timeline: EventTimeline): Int =
        timeline.events.count { it is T }

    /** Get all StateChangeEvents for a specific node. */
    fun stateChangesForNode(timeline: EventTimeline, nodeId: String): List<StateChangeEvent> =
        timeline.events.filterIsInstance<StateChangeEvent>().filter { it.nodeId == nodeId }

    /** Check that a node reaches a specific final state. */
    fun assertNodeReachesFinalState(timeline: EventTimeline, nodeId: String, expectedFinal: JobState) {
        val changes = stateChangesForNode(timeline, nodeId)
        assertTrue(changes.isNotEmpty(), "No state changes found for node '$nodeId'")
        assertEquals(
            expectedFinal, changes.last().toState,
            "Node '$nodeId' should reach $expectedFinal but reached ${changes.last().toState}"
        )
    }

    /** Verify that every leaf node in the tree has at least one state change event. */
    fun assertAllNodesHaveEvents(timeline: EventTimeline) {
        val allIds = collectNodeIds(timeline.tree)
        timeline.secondTree?.let { allIds.plus(collectNodeIds(it)) }

        for (id in allIds) {
            val hasEvent = timeline.events.any {
                when (it) {
                    is StateChangeEvent -> it.nodeId == id
                    is CancellationEvent -> it.sourceNodeId == id || it.targetNodeId == id
                    is ExceptionEvent -> it.sourceNodeId == id || it.targetNodeId == id
                    is NarrativeEvent -> false
                }
            }
            assertTrue(hasEvent, "Node '$id' has no events referencing it")
        }
    }

    /**
     * Verify that state transitions follow valid Kotlin coroutine job lifecycle.
     * Valid transitions:
     * New → Active
     * Active → Completing, Active → Cancelling, Active → Suspended (custom for visualization)
     * Suspended → Active (resume)
     * Completing → Completed
     * Cancelling → Cancelled
     */
    fun assertValidStateTransitions(timeline: EventTimeline) {
        val validTransitions = setOf(
            JobState.New to JobState.Active,
            JobState.New to JobState.Cancelled, // lazy coroutine cancelled before being started
            JobState.Active to JobState.Completing,
            JobState.Active to JobState.Cancelling,
            JobState.Active to JobState.Suspended,
            JobState.Active to JobState.Completed, // shortcut used in some scenarios
            JobState.Suspended to JobState.Active,
            JobState.Completing to JobState.Completed,
            JobState.Cancelling to JobState.Cancelled,
            // Active → Cancelled is used as a shortcut in some events (combining Cancelling+Cancelled)
            JobState.Active to JobState.Cancelled,
        )

        for (event in timeline.events) {
            if (event is StateChangeEvent) {
                val transition = event.fromState to event.toState
                assertTrue(
                    transition in validTransitions,
                    "Invalid state transition for '${event.nodeId}': " +
                            "${event.fromState} → ${event.toState}. " +
                            "Description: '${event.description}'"
                )
            }
        }
    }

    /** Run all standard validations on a timeline. */
    fun assertTimelineIsValid(timeline: EventTimeline) {
        assertUniqueNodeIds(timeline)
        assertAllEventNodeIdsExistInTree(timeline)
        assertEventsAreChronological(timeline)
        assertKotlinCodePresent(timeline)
        assertValidStateTransitions(timeline)
    }
}
