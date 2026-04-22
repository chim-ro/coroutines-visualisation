import {
  SimulationEvent,
  StateChangeEvent,
  CancellationEvent,
  ExceptionEvent,
  JobState,
  LayoutNode,
} from '../types';

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
  let delay = 200;

  // Cancel target if it's in a cancellable state
  const targetState = nodeStates.get(targetNode.id);
  const cancellableStates: JobState[] = ['Active', 'Completing', 'Suspended', 'New'];
  if (targetState && cancellableStates.includes(targetState)) {
    events.push({
      type: 'stateChange',
      delayMs: delay,
      description: `${targetNode.displayName} is being cancelled`,
      nodeId: targetNode.id,
      fromState: targetState,
      toState: 'Cancelling',
    } as StateChangeEvent);

    events.push({
      type: 'stateChange',
      delayMs: delay + 600,
      description: `${targetNode.displayName} is Cancelled`,
      nodeId: targetNode.id,
      fromState: 'Cancelling',
      toState: 'Cancelled',
    } as StateChangeEvent);
  }

  // Cancel all descendants regardless of state (structured concurrency)
  const descendants = collectDescendants(targetNode);
  for (const desc of descendants) {
    const state = nodeStates.get(desc.id);
    if (state && cancellableStates.includes(state)) {
      delay += 300;
      events.push({
        type: 'cancellation',
        delayMs: delay,
        description: `Cancellation propagates to ${desc.displayName}`,
        sourceNodeId: targetNode.id,
        targetNodeId: desc.id,
      } as CancellationEvent);

      events.push({
        type: 'stateChange',
        delayMs: delay + 100,
        description: `${desc.displayName} enters Cancelling`,
        nodeId: desc.id,
        fromState: state,
        toState: 'Cancelling',
      } as StateChangeEvent);

      events.push({
        type: 'stateChange',
        delayMs: delay + 700,
        description: `${desc.displayName} is Cancelled`,
        nodeId: desc.id,
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
  let delay = 200;

  // Fail the target node
  const targetState = nodeStates.get(targetNode.id);
  if (targetState !== 'Active') return events;

  events.push({
    type: 'stateChange',
    delayMs: delay,
    description: `${targetNode.displayName} throws: ${exceptionMessage}`,
    nodeId: targetNode.id,
    fromState: 'Active',
    toState: 'Cancelling',
  } as StateChangeEvent);
  cancelled.add(targetNode.id);

  // Cancel target's active descendants
  const descendants = collectDescendants(targetNode);
  for (const desc of descendants) {
    const state = nodeStates.get(desc.id);
    if (state === 'Active' && !cancelled.has(desc.id)) {
      delay += 200;
      events.push({
        type: 'stateChange',
        delayMs: delay,
        description: `${desc.displayName} enters Cancelling`,
        nodeId: desc.id,
        fromState: 'Active',
        toState: 'Cancelling',
      } as StateChangeEvent);
      cancelled.add(desc.id);
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
    if (parentState === 'Active' && !cancelled.has(parent.id)) {
      delay += 300;
      events.push({
        type: 'stateChange',
        delayMs: delay,
        description: `${parent.displayName} enters Cancelling`,
        nodeId: parent.id,
        fromState: 'Active',
        toState: 'Cancelling',
      } as StateChangeEvent);
      cancelled.add(parent.id);

      // Cancel siblings
      for (const sibling of parent.children) {
        if (sibling.id !== current.id && !cancelled.has(sibling.id)) {
          const sibState = nodeStates.get(sibling.id);
          if (sibState === 'Active') {
            delay += 200;
            events.push({
              type: 'cancellation',
              delayMs: delay,
              description: `${parent.displayName} cancels ${sibling.displayName}`,
              sourceNodeId: parent.id,
              targetNodeId: sibling.id,
            } as CancellationEvent);
            events.push({
              type: 'stateChange',
              delayMs: delay + 100,
              description: `${sibling.displayName} enters Cancelling`,
              nodeId: sibling.id,
              fromState: 'Active',
              toState: 'Cancelling',
            } as StateChangeEvent);
            cancelled.add(sibling.id);

            // Cancel sibling descendants
            for (const desc of collectDescendants(sibling)) {
              const descState = nodeStates.get(desc.id);
              if (descState === 'Active' && !cancelled.has(desc.id)) {
                delay += 200;
                events.push({
                  type: 'stateChange',
                  delayMs: delay,
                  description: `${desc.displayName} enters Cancelling`,
                  nodeId: desc.id,
                  fromState: 'Active',
                  toState: 'Cancelling',
                } as StateChangeEvent);
                cancelled.add(desc.id);
              }
            }
          }
        }
      }
    }

    current = parent;
  }

  // Add terminal Cancelled events for all cancelling nodes
  const allCancellingEvents = events.filter(
    e => e.type === 'stateChange' && (e as StateChangeEvent).toState === 'Cancelling'
  ) as StateChangeEvent[];

  for (const ce of allCancellingEvents) {
    events.push({
      type: 'stateChange',
      delayMs: ce.delayMs + 600,
      description: `${ce.nodeId} is Cancelled`,
      nodeId: ce.nodeId,
      fromState: 'Cancelling',
      toState: 'Cancelled',
    } as StateChangeEvent);
  }

  return events;
}

/**
 * Generate events to force-complete a node.
 */
export function generateForceCompleteEvents(
  targetNode: LayoutNode,
  nodeStates: Map<string, JobState>,
): SimulationEvent[] {
  const events: SimulationEvent[] = [];
  const targetState = nodeStates.get(targetNode.id);
  if (targetState !== 'Active') return events;

  events.push({
    type: 'stateChange',
    delayMs: 200,
    description: `${targetNode.displayName} is force-completing`,
    nodeId: targetNode.id,
    fromState: 'Active',
    toState: 'Completing',
  } as StateChangeEvent);

  events.push({
    type: 'stateChange',
    delayMs: 700,
    description: `${targetNode.displayName} is Completed`,
    nodeId: targetNode.id,
    fromState: 'Completing',
    toState: 'Completed',
  } as StateChangeEvent);

  return events;
}
