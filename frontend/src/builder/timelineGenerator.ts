import {
  EventTimeline,
  SimulationEvent,
  CoroutineNode,
  StateChangeEvent,
  ExceptionEvent,
  CancellationEvent,
} from '../types';
import { BuilderNodeConfig } from './types';

// Convert builder config to CoroutineNode tree
function toCoroutineNode(config: BuilderNodeConfig): CoroutineNode {
  return {
    id: config.id,
    displayName: config.displayName,
    builder: config.builder,
    jobType: config.jobType,
    initialState: 'New',
    children: config.children.map(toCoroutineNode),
  };
}

// Collect all nodes with depth + sibling index via BFS
interface NodeInfo {
  config: BuilderNodeConfig;
  depth: number;
  siblingIndex: number;
  parentId: string | null;
}

function collectBFS(root: BuilderNodeConfig): NodeInfo[] {
  const result: NodeInfo[] = [];
  const queue: NodeInfo[] = [{ config: root, depth: 0, siblingIndex: 0, parentId: null }];
  while (queue.length > 0) {
    const item = queue.shift()!;
    result.push(item);
    item.config.children.forEach((child, i) => {
      queue.push({ config: child, depth: item.depth + 1, siblingIndex: i, parentId: item.config.id });
    });
  }
  return result;
}

// Build a parent map from the tree
function buildParentMap(root: BuilderNodeConfig): Map<string, BuilderNodeConfig | null> {
  const map = new Map<string, BuilderNodeConfig | null>();
  const visit = (node: BuilderNodeConfig, parent: BuilderNodeConfig | null) => {
    map.set(node.id, parent);
    node.children.forEach(c => visit(c, node));
  };
  visit(root, null);
  return map;
}

// Build a node lookup map
function buildNodeMap(root: BuilderNodeConfig): Map<string, BuilderNodeConfig> {
  const map = new Map<string, BuilderNodeConfig>();
  const visit = (node: BuilderNodeConfig) => {
    map.set(node.id, node);
    node.children.forEach(visit);
  };
  visit(root);
  return map;
}

// Collect all descendants (not including self)
function collectDescendants(node: BuilderNodeConfig): BuilderNodeConfig[] {
  const result: BuilderNodeConfig[] = [];
  const visit = (n: BuilderNodeConfig) => {
    n.children.forEach(c => {
      result.push(c);
      visit(c);
    });
  };
  visit(node);
  return result;
}

function builderToKotlinCall(builder: string): string {
  switch (builder) {
    case 'Launch': return 'launch';
    case 'Async': return 'async';
    case 'RunBlocking': return 'runBlocking';
    case 'CoroutineScope': return 'coroutineScope';
    case 'SupervisorScope': return 'supervisorScope';
    default: return 'launch';
  }
}

function generateKotlinCode(node: BuilderNodeConfig, indent: number = 0): string {
  const pad = '    '.repeat(indent);
  const call = builderToKotlinCall(node.builder);
  const lines: string[] = [];

  if (indent === 0 && node.builder === 'RunBlocking') {
    lines.push(`${pad}fun main() = runBlocking {`);
  } else {
    lines.push(`${pad}${call} {`);
  }

  if (node.failure) {
    if (node.failure.timingMs > 0) {
      lines.push(`${pad}    delay(${node.failure.timingMs})`);
    }
    lines.push(`${pad}    throw RuntimeException("${node.failure.exceptionMessage}")`);
  } else if (node.children.length === 0) {
    lines.push(`${pad}    delay(500)`);
    lines.push(`${pad}    println("${node.displayName} done")`);
  }

  for (const child of node.children) {
    lines.push('');
    lines.push(generateKotlinCode(child, indent + 1));
  }

  lines.push(`${pad}}`);
  return lines.join('\n');
}

export function generateTimeline(root: BuilderNodeConfig, scenarioName: string): EventTimeline {
  const events: SimulationEvent[] = [];
  const bfsNodes = collectBFS(root);
  const parentMap = buildParentMap(root);
  const nodeMap = buildNodeMap(root);
  const cancelledNodes = new Set<string>();
  const completedNodes = new Set<string>();

  // Phase 1: Activation — BFS order, staggered by depth and sibling index
  for (const info of bfsNodes) {
    const delayMs = info.depth * 600 + info.siblingIndex * 200;
    events.push({
      type: 'stateChange',
      delayMs,
      description: `${info.config.displayName} becomes Active`,
      nodeId: info.config.id,
      fromState: 'New',
      toState: 'Active',
    } as StateChangeEvent);
  }

  // Phase 2: Failure injection — nodes with failure config
  const failingNodes = bfsNodes.filter(n => n.config.failure);

  for (const info of failingNodes) {
    const failureTime = info.config.failure!.timingMs;
    const msg = info.config.failure!.exceptionMessage;

    // Mark the failing node as Cancelling
    events.push({
      type: 'stateChange',
      delayMs: failureTime,
      description: `${info.config.displayName} throws: ${msg}`,
      nodeId: info.config.id,
      fromState: 'Active',
      toState: 'Cancelling',
    } as StateChangeEvent);
    cancelledNodes.add(info.config.id);

    // Phase 3: Exception propagation (upward)
    let currentId = info.config.id;
    let propagationDelay = failureTime + 400;

    while (true) {
      const parent = parentMap.get(currentId);
      if (!parent) break;

      // Send exception event from child to parent
      events.push({
        type: 'exception',
        delayMs: propagationDelay,
        description: `Exception propagates from ${nodeMap.get(currentId)!.displayName} to ${parent.displayName}`,
        sourceNodeId: currentId,
        targetNodeId: parent.id,
        exceptionMessage: msg,
      } as ExceptionEvent);

      // If parent is SupervisorJob or SupervisorScope, it absorbs the exception
      if (parent.jobType === 'SupervisorJob' || parent.builder === 'SupervisorScope') {
        // Supervisor absorbs — stop propagation upward
        // But still cancel the failing child's descendants
        break;
      }

      // Regular job: parent enters Cancelling, and siblings get cancelled
      if (!cancelledNodes.has(parent.id)) {
        propagationDelay += 300;
        events.push({
          type: 'stateChange',
          delayMs: propagationDelay,
          description: `${parent.displayName} enters Cancelling`,
          nodeId: parent.id,
          fromState: 'Active',
          toState: 'Cancelling',
        } as StateChangeEvent);
        cancelledNodes.add(parent.id);

        // Cancel siblings (children of parent except the failing chain)
        for (const sibling of parent.children) {
          if (sibling.id !== currentId && !cancelledNodes.has(sibling.id)) {
            propagationDelay += 200;
            events.push({
              type: 'cancellation',
              delayMs: propagationDelay,
              description: `${parent.displayName} cancels sibling ${sibling.displayName}`,
              sourceNodeId: parent.id,
              targetNodeId: sibling.id,
            } as CancellationEvent);

            events.push({
              type: 'stateChange',
              delayMs: propagationDelay + 100,
              description: `${sibling.displayName} enters Cancelling`,
              nodeId: sibling.id,
              fromState: 'Active',
              toState: 'Cancelling',
            } as StateChangeEvent);
            cancelledNodes.add(sibling.id);

            // Cancel sibling's descendants
            const sibDescendants = collectDescendants(sibling);
            let descDelay = propagationDelay + 200;
            for (const desc of sibDescendants) {
              if (!cancelledNodes.has(desc.id)) {
                descDelay += 300;
                events.push({
                  type: 'cancellation',
                  delayMs: descDelay,
                  description: `Cancellation propagates to ${desc.displayName}`,
                  sourceNodeId: sibling.id,
                  targetNodeId: desc.id,
                } as CancellationEvent);
                events.push({
                  type: 'stateChange',
                  delayMs: descDelay + 100,
                  description: `${desc.displayName} enters Cancelling`,
                  nodeId: desc.id,
                  fromState: 'Active',
                  toState: 'Cancelling',
                } as StateChangeEvent);
                cancelledNodes.add(desc.id);
              }
            }
          }
        }
      }

      currentId = parent.id;
    }

    // Phase 4: Cancel descendants of the failing node
    const descendants = collectDescendants(info.config);
    let descDelay = failureTime + 300;
    for (const desc of descendants) {
      if (!cancelledNodes.has(desc.id)) {
        descDelay += 300;
        events.push({
          type: 'cancellation',
          delayMs: descDelay,
          description: `Cancellation propagates to ${desc.displayName}`,
          sourceNodeId: info.config.id,
          targetNodeId: desc.id,
        } as CancellationEvent);
        events.push({
          type: 'stateChange',
          delayMs: descDelay + 100,
          description: `${desc.displayName} enters Cancelling`,
          nodeId: desc.id,
          fromState: 'Active',
          toState: 'Cancelling',
        } as StateChangeEvent);
        cancelledNodes.add(desc.id);
      }
    }
  }

  // Phase 5: Terminal states
  // Cancelled nodes: Cancelling → Cancelled (600ms after their Cancelling event)
  const cancellingEvents = events.filter(
    e => e.type === 'stateChange' && (e as StateChangeEvent).toState === 'Cancelling'
  ) as StateChangeEvent[];

  for (const ce of cancellingEvents) {
    events.push({
      type: 'stateChange',
      delayMs: ce.delayMs + 600,
      description: `${nodeMap.get(ce.nodeId)?.displayName ?? ce.nodeId} is Cancelled`,
      nodeId: ce.nodeId,
      fromState: 'Cancelling',
      toState: 'Cancelled',
    } as StateChangeEvent);
  }

  // Unaffected nodes: complete bottom-up (deepest first)
  const unaffectedNodes = bfsNodes
    .filter(n => !cancelledNodes.has(n.config.id))
    .sort((a, b) => b.depth - a.depth); // deepest first

  // Find the max delay so far
  let maxDelay = events.reduce((max, e) => Math.max(max, e.delayMs), 0);

  let completeDelay = maxDelay + 600;
  for (const info of unaffectedNodes) {
    events.push({
      type: 'stateChange',
      delayMs: completeDelay,
      description: `${info.config.displayName} is Completing`,
      nodeId: info.config.id,
      fromState: 'Active',
      toState: 'Completing',
    } as StateChangeEvent);

    events.push({
      type: 'stateChange',
      delayMs: completeDelay + 400,
      description: `${info.config.displayName} is Completed`,
      nodeId: info.config.id,
      fromState: 'Completing',
      toState: 'Completed',
    } as StateChangeEvent);
    completedNodes.add(info.config.id);
    completeDelay += 500;
  }

  // Phase 6: Sort and de-duplicate simultaneous events
  events.sort((a, b) => a.delayMs - b.delayMs);
  for (let i = 1; i < events.length; i++) {
    if (events[i].delayMs === events[i - 1].delayMs) {
      events[i] = { ...events[i], delayMs: events[i].delayMs + 50 };
    }
  }

  return {
    scenarioName,
    tree: toCoroutineNode(root),
    secondTree: null,
    events,
    kotlinCode: generateKotlinCode(root),
  };
}
