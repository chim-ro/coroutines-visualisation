import { CoroutineNode, EventTimeline, SimulationEvent, ExceptionEvent } from '../types';
import { BuilderNodeConfig } from './types';
import { generateTimeline } from './timelineGenerator';

/** Extract failure info from simulated events: maps sourceNodeId → { exceptionMessage, timingMs } */
function extractFailures(events: SimulationEvent[]): Map<string, { exceptionMessage: string; timingMs: number }> {
  const failures = new Map<string, { exceptionMessage: string; timingMs: number }>();
  for (const event of events) {
    if (event.type === 'exception') {
      const ee = event as ExceptionEvent;
      if (!failures.has(ee.sourceNodeId)) {
        failures.set(ee.sourceNodeId, {
          exceptionMessage: ee.exceptionMessage,
          timingMs: ee.delayMs,
        });
      }
    }
  }
  return failures;
}

/** Convert a CoroutineNode tree (from backend) to a BuilderNodeConfig (for the generator),
 *  pre-populating failure info from the original simulated events */
export function coroutineNodeToBuilder(node: CoroutineNode, failures?: Map<string, { exceptionMessage: string; timingMs: number }>): BuilderNodeConfig {
  const failure = failures?.get(node.id);
  return {
    id: node.id,
    displayName: node.displayName,
    builder: node.builder,
    jobType: node.jobType,
    children: node.children.map(c => coroutineNodeToBuilder(c, failures)),
    ...(failure ? { failure } : {}),
  };
}

/** Convert a CoroutineNode tree + events to a BuilderNodeConfig with failure info extracted */
export function coroutineNodeToBuilderWithEvents(node: CoroutineNode, events: SimulationEvent[]): BuilderNodeConfig {
  return coroutineNodeToBuilder(node, extractFailures(events));
}

/** Deep-clone a BuilderNodeConfig tree, prefixing all IDs */
export function prefixNodeIds(tree: BuilderNodeConfig, prefix: string): BuilderNodeConfig {
  return {
    ...tree,
    id: `${prefix}${tree.id}`,
    children: tree.children.map(c => prefixNodeIds(c, prefix)),
  };
}


/**
 * Generate a merged timeline for side-by-side comparison.
 * Left side uses the ORIGINAL backend timeline (preserving hand-crafted events).
 * Right side generates from the modified builder tree with "right-" prefixed IDs.
 */
export function generateComparisonTimeline(
  originalTimeline: EventTimeline,
  rightTree: BuilderNodeConfig,
  rightLabel: string,
): EventTimeline {
  const leftLabel = originalTimeline.scenarioName;

  // Left side: use the original backend events as-is
  const taggedLeftEvents = originalTimeline.events.map(e => ({
    ...e,
    description: `[Left] ${e.description}`,
  }));

  // Right side: generate from modified builder tree with prefixed IDs
  const rightPrefixed = prefixNodeIds(rightTree, 'right-');
  const rightTimeline = generateTimeline(rightPrefixed, rightLabel);

  const taggedRightEvents = rightTimeline.events.map(e => ({
    ...e,
    description: `[Right] ${e.description}`,
  }));

  // Merge events sorted by delayMs, with 50ms minimum spacing
  const merged = [...taggedLeftEvents, ...taggedRightEvents].sort((a, b) => a.delayMs - b.delayMs);
  for (let i = 1; i < merged.length; i++) {
    if (merged[i].delayMs <= merged[i - 1].delayMs) {
      merged[i] = { ...merged[i], delayMs: merged[i - 1].delayMs + 50 };
    }
  }

  // Right tree with prefixed IDs for secondTree
  const rightCoroutineTree = rightTimeline.tree;

  return {
    scenarioName: `${leftLabel} vs ${rightLabel}`,
    tree: originalTimeline.tree,
    secondTree: rightCoroutineTree,
    events: merged,
    kotlinCode: `// Left: ${leftLabel} (original)\n${originalTimeline.kotlinCode}\n\n// Right: ${rightLabel}\n${rightTimeline.kotlinCode}`,
  };
}
