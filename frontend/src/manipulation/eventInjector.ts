import {
  SimulationEvent,
  StateChangeEvent,
  CancellationEvent,
  ExceptionEvent,
  JobState,
  LayoutNode,
} from '../types';

// Find a node by ID within a subtree
function findNodeInTree(root: LayoutNode, nodeId: string): LayoutNode | null {
  if (root.id === nodeId) return root;
  for (const child of root.children) {
    const found = findNodeInTree(child, nodeId);
    if (found) return found;
  }
  return null;
}

// Collect all descendants of a layout node
function collectDescendants(node: LayoutNode): LayoutNode[] {
  const result: LayoutNode[] = [];
  const visit = (n: LayoutNode) => {
    n.children.forEach(c => {
      result.push(c);
      visit(c);
    });
  };
  visit(node);
  return result;
}

// Build a flat map of all nodes
function buildNodeMap(root: LayoutNode): Map<string, LayoutNode> {
  const map = new Map<string, LayoutNode>();
  const visit = (n: LayoutNode) => {
    map.set(n.id, n);
    n.children.forEach(visit);
  };
  visit(root);
  return map;
}

/**
 * Generate events to cancel a node and its active descendants.
 * delayMs values are relative (will be offset by injectEvents).
 */
export function generateCancelEvents(
  targetNode: LayoutNode,
  nodeStates: Map<string, JobState>,
): SimulationEvent[] {
  const events: SimulationEvent[] = [];
  const cancellableStates: JobState[] = ['Active', 'Completing', 'Suspended', 'New'];
  const cancellingTimeMap = new Map<string, number>(); // nodeId → Cancelling delayMs

  // Cancel target if it's in a cancellable state
  const targetState = nodeStates.get(targetNode.id);
  let delay = 200;

  if (targetState && cancellableStates.includes(targetState)) {
    if (targetState === 'New') {
      events.push({
        type: 'stateChange',
        delayMs: delay,
        description: `${targetNode.displayName} is Cancelled (never started)`,
        nodeId: targetNode.id,
        fromState: 'New',
        toState: 'Cancelled',
      } as StateChangeEvent);
    } else {
      events.push({
        type: 'stateChange',
        delayMs: delay,
        description: `${targetNode.displayName} is being cancelled`,
        nodeId: targetNode.id,
        fromState: targetState,
        toState: 'Cancelling',
      } as StateChangeEvent);
      cancellingTimeMap.set(targetNode.id, delay);
    }
  }

  // Cancel descendants level-by-level using BFS for correct source attribution
  const queue: LayoutNode[] = [targetNode];
  while (queue.length > 0) {
    const parent = queue.shift()!;
    for (const child of parent.children) {
      const state = nodeStates.get(child.id);
      if (state && cancellableStates.includes(state)) {
        delay += 300;
        events.push({
          type: 'cancellation',
          delayMs: delay,
          description: `Cancellation propagates to ${child.displayName}`,
          sourceNodeId: parent.id,
          targetNodeId: child.id,
        } as CancellationEvent);

        if (state === 'New') {
          events.push({
            type: 'stateChange',
            delayMs: delay + 100,
            description: `${child.displayName} is Cancelled (never started)`,
            nodeId: child.id,
            fromState: 'New',
            toState: 'Cancelled',
          } as StateChangeEvent);
        } else {
          events.push({
            type: 'stateChange',
            delayMs: delay + 100,
            description: `${child.displayName} enters Cancelling`,
            nodeId: child.id,
            fromState: state,
            toState: 'Cancelling',
          } as StateChangeEvent);
          cancellingTimeMap.set(child.id, delay + 100);
        }
      }
      queue.push(child);
    }
  }

  // Add Cancelled events bottom-up: parents wait for all children
  const getCancelledTime = (node: LayoutNode): number => {
    const ownTime = cancellingTimeMap.get(node.id);
    if (ownTime === undefined) return 0;

    let maxChildCancelledTime = 0;
    for (const child of node.children) {
      if (cancellingTimeMap.has(child.id)) {
        maxChildCancelledTime = Math.max(maxChildCancelledTime, getCancelledTime(child));
      }
    }

    return Math.max(ownTime + 600, maxChildCancelledTime + 200);
  };

  for (const [nodeId, _] of cancellingTimeMap) {
    const node = findNodeInTree(targetNode, nodeId);
    if (node) {
      events.push({
        type: 'stateChange',
        delayMs: getCancelledTime(node),
        description: `${node.displayName} is Cancelled`,
        nodeId: node.id,
        fromState: 'Cancelling',
        toState: 'Cancelled',
      } as StateChangeEvent);
    }
  }

  return events;
}

/**
 * Generate events to inject an exception at a node and propagate upward
 * using structured concurrency rules.
 */
export function generateExceptionEvents(
  targetNode: LayoutNode,
  nodeStates: Map<string, JobState>,
  exceptionMessage: string,
  layoutRoot: LayoutNode,
): SimulationEvent[] {
  const events: SimulationEvent[] = [];
  const nodeMap = buildNodeMap(layoutRoot);
  const cancelled = new Set<string>();
  const cancellableStates: JobState[] = ['Active', 'Completing', 'Suspended', 'New'];
  let delay = 200;

  // Fail the target node
  const targetState = nodeStates.get(targetNode.id);
  if (targetState !== 'Active') return events;

  // Helper to cancel a node in any cancellable state
  const cancelNode = (node: LayoutNode, atDelay: number): void => {
    if (cancelled.has(node.id)) return;
    const state = nodeStates.get(node.id);
    if (!state || !cancellableStates.includes(state)) return;

    if (state === 'New') {
      events.push({
        type: 'stateChange',
        delayMs: atDelay,
        description: `${node.displayName} is Cancelled (never started)`,
        nodeId: node.id,
        fromState: 'New',
        toState: 'Cancelled',
      } as StateChangeEvent);
    } else {
      events.push({
        type: 'stateChange',
        delayMs: atDelay,
        description: `${node.displayName} enters Cancelling`,
        nodeId: node.id,
        fromState: state,
        toState: 'Cancelling',
      } as StateChangeEvent);
    }
    cancelled.add(node.id);
  };

  events.push({
    type: 'stateChange',
    delayMs: delay,
    description: `${targetNode.displayName} throws: ${exceptionMessage}`,
    nodeId: targetNode.id,
    fromState: 'Active',
    toState: 'Cancelling',
  } as StateChangeEvent);
  cancelled.add(targetNode.id);

  // Cancel target's descendants in all cancellable states
  const descendants = collectDescendants(targetNode);
  for (const desc of descendants) {
    if (!cancelled.has(desc.id)) {
      const state = nodeStates.get(desc.id);
      if (state && cancellableStates.includes(state)) {
        delay += 200;
        cancelNode(desc, delay);
      }
    }
  }

  // Propagate exception upward
  let current = targetNode;
  delay += 400;

  while (current.parent) {
    const parent = current.parent;

    events.push({
      type: 'exception',
      delayMs: delay,
      description: `Exception propagates from ${current.displayName} to ${parent.displayName}`,
      sourceNodeId: current.id,
      targetNodeId: parent.id,
      exceptionMessage,
    } as ExceptionEvent);

    // SupervisorJob or SupervisorScope absorbs
    if (parent.jobType === 'SupervisorJob' || parent.builder === 'SupervisorScope') {
      break;
    }

    // Regular job: cancel parent and siblings
    const parentState = nodeStates.get(parent.id);
    if (parentState && cancellableStates.includes(parentState) && !cancelled.has(parent.id)) {
      delay += 300;
      cancelNode(parent, delay);

      // Cancel siblings in any cancellable state
      for (const sibling of parent.children) {
        if (sibling.id !== current.id && !cancelled.has(sibling.id)) {
          const sibState = nodeStates.get(sibling.id);
          if (sibState && cancellableStates.includes(sibState)) {
            delay += 200;
            events.push({
              type: 'cancellation',
              delayMs: delay,
              description: `${parent.displayName} cancels ${sibling.displayName}`,
              sourceNodeId: parent.id,
              targetNodeId: sibling.id,
            } as CancellationEvent);
            cancelNode(sibling, delay + 100);

            // Cancel sibling descendants in any cancellable state
            for (const desc of collectDescendants(sibling)) {
              const descState = nodeStates.get(desc.id);
              if (descState && cancellableStates.includes(descState) && !cancelled.has(desc.id)) {
                delay += 200;
                cancelNode(desc, delay);
              }
            }
          }
        }
      }
    }

    current = parent;
  }

  // Add terminal Cancelled events bottom-up: parents wait for all children
  const cancellingEvents = events.filter(
    e => e.type === 'stateChange' && (e as StateChangeEvent).toState === 'Cancelling'
  ) as StateChangeEvent[];

  const cancellingTimeMap = new Map<string, number>();
  for (const ce of cancellingEvents) {
    cancellingTimeMap.set(ce.nodeId, ce.delayMs);
  }

  const getCancelledTime = (nodeId: string): number => {
    const ownTime = cancellingTimeMap.get(nodeId);
    if (ownTime === undefined) return 0;

    const node = nodeMap.get(nodeId);
    if (!node) return ownTime + 600;

    let maxChildCancelledTime = 0;
    for (const child of node.children) {
      if (cancellingTimeMap.has(child.id)) {
        maxChildCancelledTime = Math.max(maxChildCancelledTime, getCancelledTime(child.id));
      }
    }

    return Math.max(ownTime + 600, maxChildCancelledTime + 200);
  };

  for (const ce of cancellingEvents) {
    events.push({
      type: 'stateChange',
      delayMs: getCancelledTime(ce.nodeId),
      description: `${nodeMap.get(ce.nodeId)?.displayName ?? ce.nodeId} is Cancelled`,
      nodeId: ce.nodeId,
      fromState: 'Cancelling',
      toState: 'Cancelled',
    } as StateChangeEvent);
  }

  return events;
}

/**
 * Generate events to force-complete a node.
 * Structured concurrency: children must complete before parent.
 */
export function generateForceCompleteEvents(
  targetNode: LayoutNode,
  nodeStates: Map<string, JobState>,
): SimulationEvent[] {
  const events: SimulationEvent[] = [];
  const targetState = nodeStates.get(targetNode.id);
  if (targetState !== 'Active') return events;

  // Collect completable descendants deepest-first (structured concurrency: children complete before parent)
  const descendants = collectDescendants(targetNode);
  const completableStates: JobState[] = ['Active', 'Suspended'];
  const completableDescendants = descendants
    .filter(d => {
      const s = nodeStates.get(d.id);
      return s !== undefined && completableStates.includes(s);
    })
    .reverse(); // deepest first (collectDescendants returns pre-order DFS)

  let delay = 200;

  // Complete children bottom-up first
  for (const desc of completableDescendants) {
    const descState = nodeStates.get(desc.id) ?? 'Active';
    events.push({
      type: 'stateChange',
      delayMs: delay,
      description: `${desc.displayName} is Completing`,
      nodeId: desc.id,
      fromState: descState,
      toState: 'Completing',
    } as StateChangeEvent);

    events.push({
      type: 'stateChange',
      delayMs: delay + 400,
      description: `${desc.displayName} is Completed`,
      nodeId: desc.id,
      fromState: 'Completing',
      toState: 'Completed',
    } as StateChangeEvent);

    delay += 500;
  }

  // Then complete the target node itself
  events.push({
    type: 'stateChange',
    delayMs: delay,
    description: `${targetNode.displayName} is force-completing`,
    nodeId: targetNode.id,
    fromState: 'Active',
    toState: 'Completing',
  } as StateChangeEvent);

  events.push({
    type: 'stateChange',
    delayMs: delay + 500,
    description: `${targetNode.displayName} is Completed`,
    nodeId: targetNode.id,
    fromState: 'Completing',
    toState: 'Completed',
  } as StateChangeEvent);

  return events;
}
