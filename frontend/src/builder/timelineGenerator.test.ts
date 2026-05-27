import { describe, it, expect } from 'vitest';
import { generateTimeline } from './timelineGenerator';
import { BuilderNodeConfig } from './types';
import { StateChangeEvent, ExceptionEvent, CancellationEvent, EventTimeline } from '../types';

// ─── Helpers ─────────────────────────────────────────────────────

function stateChanges(timeline: EventTimeline): StateChangeEvent[] {
  return timeline.events.filter((e): e is StateChangeEvent => e.type === 'stateChange');
}

function exceptions(timeline: EventTimeline): ExceptionEvent[] {
  return timeline.events.filter((e): e is ExceptionEvent => e.type === 'exception');
}

function cancellations(timeline: EventTimeline): CancellationEvent[] {
  return timeline.events.filter((e): e is CancellationEvent => e.type === 'cancellation');
}

function stateChangesFor(timeline: EventTimeline, nodeId: string): StateChangeEvent[] {
  return stateChanges(timeline).filter(e => e.nodeId === nodeId);
}

function finalState(timeline: EventTimeline, nodeId: string): string {
  const changes = stateChangesFor(timeline, nodeId);
  return changes[changes.length - 1].toState;
}

type IdTree = { id: string; children: IdTree[] };
function collectNodeIds(tree: IdTree): string[] {
  const ids: string[] = [tree.id];
  for (const child of tree.children) {
    ids.push(...collectNodeIds(child));
  }
  return ids;
}

/** Verify events are sorted by delayMs (monotonically non-decreasing). */
function assertChronological(timeline: EventTimeline) {
  for (let i = 1; i < timeline.events.length; i++) {
    expect(timeline.events[i].delayMs).toBeGreaterThanOrEqual(
      timeline.events[i - 1].delayMs
    );
  }
}

/** Verify no stateChange has fromState === toState. */
function assertNoNoopTransitions(timeline: EventTimeline) {
  for (const e of stateChanges(timeline)) {
    expect(e.fromState).not.toBe(e.toState);
  }
}

/** Verify that all stateChange nodeIds exist in the tree. */
function assertAllNodeIdsExist(timeline: EventTimeline) {
  const treeIds = new Set(collectNodeIds(timeline.tree));
  for (const e of stateChanges(timeline)) {
    expect(treeIds.has(e.nodeId)).toBe(true);
  }
  for (const e of exceptions(timeline)) {
    expect(treeIds.has(e.sourceNodeId)).toBe(true);
    expect(treeIds.has(e.targetNodeId)).toBe(true);
  }
  for (const e of cancellations(timeline)) {
    expect(treeIds.has(e.sourceNodeId)).toBe(true);
    expect(treeIds.has(e.targetNodeId)).toBe(true);
  }
}

/** Verify state transition consistency per node: each event's fromState matches the prior toState. */
function assertTransitionConsistency(timeline: EventTimeline) {
  const lastState = new Map<string, string>();
  // All nodes start as 'New'
  for (const id of collectNodeIds(timeline.tree)) {
    lastState.set(id, 'New');
  }
  for (const e of stateChanges(timeline)) {
    expect(e.fromState).toBe(lastState.get(e.nodeId));
    lastState.set(e.nodeId, e.toState);
  }
}

function assertValid(timeline: EventTimeline) {
  assertChronological(timeline);
  assertNoNoopTransitions(timeline);
  assertAllNodeIdsExist(timeline);
  assertTransitionConsistency(timeline);
  expect(timeline.kotlinCode.length).toBeGreaterThan(0);
}

// ─── Tree builders ───────────────────────────────────────────────

function node(
  id: string,
  displayName: string,
  builder: BuilderNodeConfig['builder'] = 'Launch',
  jobType: BuilderNodeConfig['jobType'] = 'Job',
  children: BuilderNodeConfig[] = [],
  failure?: BuilderNodeConfig['failure']
): BuilderNodeConfig {
  return { id, displayName, builder, jobType, children, failure };
}

function root(children: BuilderNodeConfig[] = []): BuilderNodeConfig {
  return node('root', 'runBlocking', 'RunBlocking', 'Job', children);
}

// ═════════════════════════════════════════════════════════════════
// 10 TESTS — Easy → Very Complex
// ═════════════════════════════════════════════════════════════════

describe('generateTimeline', () => {

  // ── Test 1: Single root node, no children ──────────────────────
  it('1. single root node completes through New → Active → Completing → Completed', () => {
    const tree = root();
    const timeline = generateTimeline(tree, 'Single root');

    assertValid(timeline);

    expect(timeline.scenarioName).toBe('Single root');
    expect(timeline.tree.initialState).toBe('New');
    expect(collectNodeIds(timeline.tree)).toEqual(['root']);

    // root: New → Active → Completing → Completed
    const rootChanges = stateChangesFor(timeline, 'root');
    expect(rootChanges.map(e => e.toState)).toEqual(['Active', 'Completing', 'Completed']);
    expect(finalState(timeline, 'root')).toBe('Completed');

    expect(exceptions(timeline)).toHaveLength(0);
    expect(cancellations(timeline)).toHaveLength(0);
  });

  // ── Test 2: Root with one child — structured concurrency ordering ─
  it('2. root with one child: child completes before root', () => {
    const tree = root([
      node('child', 'launch #1'),
    ]);
    const timeline = generateTimeline(tree, 'One child');

    assertValid(timeline);

    expect(finalState(timeline, 'child')).toBe('Completed');
    expect(finalState(timeline, 'root')).toBe('Completed');

    // Child's Completed must appear before root's Completing
    const sc = stateChanges(timeline);
    const childDone = sc.findIndex(e => e.nodeId === 'child' && e.toState === 'Completed');
    const rootCompleting = sc.findIndex(e => e.nodeId === 'root' && e.toState === 'Completing');
    expect(childDone).toBeLessThan(rootCompleting);
  });

  // ── Test 3: Root with 3 children, all complete (happy path) ────
  it('3. root with 3 children: all complete bottom-up', () => {
    const tree = root([
      node('a', 'launch A'),
      node('b', 'async B', 'Async'),
      node('c', 'launch C'),
    ]);
    const timeline = generateTimeline(tree, 'Three children');

    assertValid(timeline);

    expect(collectNodeIds(timeline.tree)).toHaveLength(4);
    expect(finalState(timeline, 'a')).toBe('Completed');
    expect(finalState(timeline, 'b')).toBe('Completed');
    expect(finalState(timeline, 'c')).toBe('Completed');
    expect(finalState(timeline, 'root')).toBe('Completed');

    // Activation order follows BFS with sibling staggering
    const activations = stateChanges(timeline).filter(e => e.toState === 'Active');
    const rootAct = activations.find(e => e.nodeId === 'root')!;
    const aAct = activations.find(e => e.nodeId === 'a')!;
    const bAct = activations.find(e => e.nodeId === 'b')!;
    const cAct = activations.find(e => e.nodeId === 'c')!;
    expect(rootAct.delayMs).toBeLessThan(aAct.delayMs);
    expect(aAct.delayMs).toBeLessThan(bAct.delayMs);
    expect(bAct.delayMs).toBeLessThan(cAct.delayMs);
  });

  // ── Test 4: Single child fails — exception propagates, root cancelled ─
  it('4. single child throws exception: propagates to root, both cancelled', () => {
    const tree = root([
      node('bad', 'launch (fails)', 'Launch', 'Job', [], {
        exceptionMessage: 'boom',
        timingMs: 1000,
      }),
    ]);
    const timeline = generateTimeline(tree, 'Single failure');

    assertValid(timeline);

    expect(finalState(timeline, 'bad')).toBe('Cancelled');
    expect(finalState(timeline, 'root')).toBe('Cancelled');

    // Exception from bad → root
    const exs = exceptions(timeline);
    expect(exs).toHaveLength(1);
    expect(exs[0].sourceNodeId).toBe('bad');
    expect(exs[0].targetNodeId).toBe('root');
    expect(exs[0].exceptionMessage).toBe('boom');
  });

  // ── Test 5: Failure cancels siblings ───────────────────────────
  it('5. child-2 fails: siblings child-1 and child-3 are cancelled', () => {
    const tree = root([
      node('c1', 'launch #1'),
      node('c2', 'launch #2 (fails)', 'Launch', 'Job', [], {
        exceptionMessage: 'error in c2',
        timingMs: 800,
      }),
      node('c3', 'launch #3'),
    ]);
    const timeline = generateTimeline(tree, 'Sibling cancel');

    assertValid(timeline);

    expect(finalState(timeline, 'c2')).toBe('Cancelled');
    expect(finalState(timeline, 'c1')).toBe('Cancelled');
    expect(finalState(timeline, 'c3')).toBe('Cancelled');
    expect(finalState(timeline, 'root')).toBe('Cancelled');

    // Cancellation events from root to siblings
    const cxs = cancellations(timeline);
    expect(cxs.some(e => e.sourceNodeId === 'root' && e.targetNodeId === 'c1')).toBe(true);
    expect(cxs.some(e => e.sourceNodeId === 'root' && e.targetNodeId === 'c3')).toBe(true);
  });

  // ── Test 6: SupervisorScope absorbs exception, siblings survive ─
  it('6. supervisorScope absorbs child failure: siblings complete normally', () => {
    const tree = node('root', 'supervisorScope', 'SupervisorScope', 'SupervisorJob', [
      node('good', 'launch (good)'),
      node('bad', 'launch (bad)', 'Launch', 'Job', [], {
        exceptionMessage: 'supervised failure',
        timingMs: 500,
      }),
      node('also-good', 'launch (also good)'),
    ]);
    const timeline = generateTimeline(tree, 'Supervisor absorb');

    assertValid(timeline);

    // Only the bad child is cancelled
    expect(finalState(timeline, 'bad')).toBe('Cancelled');

    // Siblings and supervisor complete
    expect(finalState(timeline, 'good')).toBe('Completed');
    expect(finalState(timeline, 'also-good')).toBe('Completed');
    expect(finalState(timeline, 'root')).toBe('Completed');

    // Exception event reaches supervisor but stops there
    const exs = exceptions(timeline);
    expect(exs).toHaveLength(1);
    expect(exs[0].sourceNodeId).toBe('bad');
    expect(exs[0].targetNodeId).toBe('root');

    // No cancellation events to siblings
    const cxs = cancellations(timeline);
    expect(cxs.filter(e => e.targetNodeId === 'good')).toHaveLength(0);
    expect(cxs.filter(e => e.targetNodeId === 'also-good')).toHaveLength(0);
  });

  // ── Test 7: Deep tree (3 levels) — bottom-up completion ordering ─
  it('7. deep 3-level tree: grandchildren complete before children before root', () => {
    const tree = root([
      node('a', 'launch A', 'Launch', 'Job', [
        node('a1', 'launch A1'),
        node('a2', 'launch A2'),
      ]),
      node('b', 'launch B', 'Launch', 'Job', [
        node('b1', 'launch B1'),
      ]),
    ]);
    const timeline = generateTimeline(tree, 'Deep tree');

    assertValid(timeline);

    expect(collectNodeIds(timeline.tree)).toHaveLength(6);

    // All complete
    for (const id of ['a1', 'a2', 'b1', 'a', 'b', 'root']) {
      expect(finalState(timeline, id)).toBe('Completed');
    }

    // Bottom-up ordering: grandchildren before parents before root
    const sc = stateChanges(timeline);
    const idx = (id: string) => sc.findIndex(e => e.nodeId === id && e.toState === 'Completed');
    expect(idx('a1')).toBeLessThan(idx('a'));
    expect(idx('a2')).toBeLessThan(idx('a'));
    expect(idx('b1')).toBeLessThan(idx('b'));
    expect(idx('a')).toBeLessThan(idx('root'));
    expect(idx('b')).toBeLessThan(idx('root'));

    // Activation uses BFS: root (depth 0), then a/b (depth 1), then a1/a2/b1 (depth 2)
    const acts = stateChanges(timeline).filter(e => e.toState === 'Active');
    const actTime = (id: string) => acts.find(e => e.nodeId === id)!.delayMs;
    expect(actTime('root')).toBeLessThan(actTime('a'));
    expect(actTime('a')).toBeLessThan(actTime('a1'));
    expect(actTime('b')).toBeLessThan(actTime('b1'));
  });

  // ── Test 8: Failure cascades through children, cancels descendants ─
  it('8. grandchild failure propagates upward and cancels uncle subtree', () => {
    const tree = root([
      node('a', 'launch A', 'Launch', 'Job', [
        node('a1', 'launch A1 (fails)', 'Launch', 'Job', [], {
          exceptionMessage: 'deep boom',
          timingMs: 1200,
        }),
      ]),
      node('b', 'launch B', 'Launch', 'Job', [
        node('b1', 'launch B1'),
        node('b2', 'launch B2'),
      ]),
    ]);
    const timeline = generateTimeline(tree, 'Deep failure');

    assertValid(timeline);

    // a1 fails → a enters Cancelling → root enters Cancelling
    // → b and its descendants are cancelled
    expect(finalState(timeline, 'a1')).toBe('Cancelled');
    expect(finalState(timeline, 'a')).toBe('Cancelled');
    expect(finalState(timeline, 'root')).toBe('Cancelled');

    // Uncle subtree (b, b1, b2) all cancelled
    expect(finalState(timeline, 'b')).toBe('Cancelled');
    expect(finalState(timeline, 'b1')).toBe('Cancelled');
    expect(finalState(timeline, 'b2')).toBe('Cancelled');

    // Exception chain: a1 → a → root
    const exs = exceptions(timeline);
    expect(exs.some(e => e.sourceNodeId === 'a1' && e.targetNodeId === 'a')).toBe(true);
    expect(exs.some(e => e.sourceNodeId === 'a' && e.targetNodeId === 'root')).toBe(true);

    // Cancellation to siblings: root → b (and b's descendants)
    const cxs = cancellations(timeline);
    expect(cxs.some(e => e.sourceNodeId === 'root' && e.targetNodeId === 'b')).toBe(true);
  });

  // ── Test 9: SupervisorScope inside regular scope — mixed propagation ─
  it('9. supervisorScope nested in regular root: failure in supervisor child stays contained, sibling of supervisor outside still survives', () => {
    // root (runBlocking)
    //   ├── supervisor (supervisorScope)
    //   │     ├── ok (launch)
    //   │     └── fail (launch, throws)
    //   └── outside (launch)
    const tree = root([
      node('supervisor', 'supervisorScope', 'SupervisorScope', 'SupervisorJob', [
        node('ok', 'launch (ok)'),
        node('fail', 'launch (fail)', 'Launch', 'Job', [], {
          exceptionMessage: 'contained failure',
          timingMs: 700,
        }),
      ]),
      node('outside', 'launch (outside)'),
    ]);
    const timeline = generateTimeline(tree, 'Nested supervisor');

    assertValid(timeline);

    // fail is cancelled, supervisor absorbs
    expect(finalState(timeline, 'fail')).toBe('Cancelled');

    // ok survives (supervisor absorbs)
    expect(finalState(timeline, 'ok')).toBe('Completed');

    // supervisor completes (absorbed the exception)
    expect(finalState(timeline, 'supervisor')).toBe('Completed');

    // outside is unaffected
    expect(finalState(timeline, 'outside')).toBe('Completed');

    // root completes
    expect(finalState(timeline, 'root')).toBe('Completed');

    // Exception stops at supervisor (1 exception event: fail → supervisor)
    const exs = exceptions(timeline);
    expect(exs).toHaveLength(1);
    expect(exs[0].sourceNodeId).toBe('fail');
    expect(exs[0].targetNodeId).toBe('supervisor');
  });

  // ── Test 10: Very complex — 4-level tree with supervisor root, mixed failures ─
  it('10. complex 4-level tree: supervisor root absorbs branchA failure while branchB cascades failure through coroutineScope', () => {
    // root (supervisorScope, SupervisorJob)
    //   ├── branchA (launch)
    //   │     ├── a1 (launch)
    //   │     └── a2 (launch, throws at 600ms)
    //   ├── branchB (launch)
    //   │     └── scope (coroutineScope)
    //   │           ├── c1 (launch)
    //   │           └── c2 (launch, throws at 900ms)
    //   └── branchC (launch)
    //         └── c3 (launch)
    const tree = node('root', 'supervisorScope', 'SupervisorScope', 'SupervisorJob', [
      node('branchA', 'launch A', 'Launch', 'Job', [
        node('a1', 'launch A1'),
        node('a2', 'launch A2 (fails)', 'Launch', 'Job', [], {
          exceptionMessage: 'A2 error',
          timingMs: 600,
        }),
      ]),
      node('branchB', 'launch B', 'Launch', 'Job', [
        node('scope', 'coroutineScope', 'CoroutineScope', 'Job', [
          node('c1', 'launch C1'),
          node('c2', 'launch C2 (fails)', 'Launch', 'Job', [], {
            exceptionMessage: 'C2 error',
            timingMs: 900,
          }),
        ]),
      ]),
      node('branchC', 'launch C', 'Launch', 'Job', [
        node('c3', 'launch C3'),
      ]),
    ]);
    const timeline = generateTimeline(tree, 'Complex mixed');

    assertValid(timeline);

    // === Branch A: a2 fails, branchA cascades, supervisor absorbs ===
    expect(finalState(timeline, 'a2')).toBe('Cancelled');
    expect(finalState(timeline, 'a1')).toBe('Cancelled');
    expect(finalState(timeline, 'branchA')).toBe('Cancelled');

    // === Branch B: c2 fails inside coroutineScope → scope cascades → branchB cascades → supervisor absorbs ===
    expect(finalState(timeline, 'c2')).toBe('Cancelled');
    expect(finalState(timeline, 'c1')).toBe('Cancelled');
    expect(finalState(timeline, 'scope')).toBe('Cancelled');
    expect(finalState(timeline, 'branchB')).toBe('Cancelled');

    // === Branch C: completely unaffected by failures in A and B ===
    expect(finalState(timeline, 'branchC')).toBe('Completed');
    expect(finalState(timeline, 'c3')).toBe('Completed');

    // === Root (supervisor): absorbs both failures, completes ===
    expect(finalState(timeline, 'root')).toBe('Completed');

    // Exception chain for branchA: a2 → branchA → root (absorbed)
    const exs = exceptions(timeline);
    expect(exs.some(e => e.sourceNodeId === 'a2' && e.targetNodeId === 'branchA')).toBe(true);
    expect(exs.some(e => e.sourceNodeId === 'branchA' && e.targetNodeId === 'root')).toBe(true);

    // Exception chain for branchB: c2 → scope → branchB → root (absorbed)
    expect(exs.some(e => e.sourceNodeId === 'c2' && e.targetNodeId === 'scope')).toBe(true);
    expect(exs.some(e => e.sourceNodeId === 'scope' && e.targetNodeId === 'branchB')).toBe(true);
    expect(exs.some(e => e.sourceNodeId === 'branchB' && e.targetNodeId === 'root')).toBe(true);

    // No cancellation events to branchC (supervisor protects it)
    const cxs = cancellations(timeline);
    expect(cxs.some(e => e.targetNodeId === 'branchC')).toBe(false);
    expect(cxs.some(e => e.targetNodeId === 'c3')).toBe(false);

    // Kotlin code generated
    expect(timeline.kotlinCode).toContain('supervisorScope');
    expect(timeline.kotlinCode).toContain('coroutineScope');
    expect(timeline.kotlinCode).toContain('A2 error');
    expect(timeline.kotlinCode).toContain('C2 error');

    // Total node count: 10
    expect(collectNodeIds(timeline.tree)).toHaveLength(10);
  });

  // ── Test 11: shallow node fails early above a deep descendant chain ─
  // Regression: Phase-1 activation is depth-based, but an early failure cancels
  // deep descendants on a faster clock. Every node must still go
  // New → Active → Cancelling → Cancelled in order (no New→Cancelling→Active).
  it('11. early shallow failure with deep descendant chain keeps legal lifecycle ordering', () => {
    const tree = root([
      node('a', 'launch A (fails early)', 'Launch', 'Job', [
        node('a1', 'launch A1', 'Launch', 'Job', [
          node('a2', 'launch A2', 'Launch', 'Job', [
            node('a3', 'launch A3'),
          ]),
        ]),
      ], { exceptionMessage: 'early boom', timingMs: 0 }),
    ]);
    const timeline = generateTimeline(tree, 'Deep cancel ordering');

    // assertValid includes transition consistency — this is the core regression check.
    assertValid(timeline);

    // Every node in the chain ends Cancelled.
    for (const id of ['a', 'a1', 'a2', 'a3', 'root']) {
      expect(finalState(timeline, id)).toBe('Cancelled');
    }

    // The deepest node must reach Active before it reaches Cancelling.
    const a3 = stateChangesFor(timeline, 'a3');
    const a3Active = a3.findIndex(e => e.toState === 'Active');
    const a3Cancelling = a3.findIndex(e => e.toState === 'Cancelling');
    expect(a3Active).toBeGreaterThanOrEqual(0);
    expect(a3Active).toBeLessThan(a3Cancelling);
    // Canonical lifecycle, in order:
    expect(a3.map(e => e.toState)).toEqual(['Active', 'Cancelling', 'Cancelled']);
  });

  // ── Test 12: async child failure under a regular scope propagates ───
  // Training (l-105/108): an async failing under coroutineScope/regular Job
  // propagates to the parent and cancels siblings (the exception is NOT
  // silently confined the way it is under a supervisor).
  it('12. failing async under a regular scope propagates and cancels siblings', () => {
    const tree = root([
      node('producer', 'async (fails)', 'Async', 'Job', [], {
        exceptionMessage: 'async boom',
        timingMs: 600,
      }),
      node('sibling', 'launch (sibling)'),
    ]);
    const timeline = generateTimeline(tree, 'Async failure');

    assertValid(timeline);

    expect(finalState(timeline, 'producer')).toBe('Cancelled');
    expect(finalState(timeline, 'sibling')).toBe('Cancelled');
    expect(finalState(timeline, 'root')).toBe('Cancelled');

    // Exception propagates up from the async to the scope...
    const exs = exceptions(timeline);
    expect(exs.some(e => e.sourceNodeId === 'producer' && e.targetNodeId === 'root')).toBe(true);
    // ...and the scope cancels the sibling.
    const cxs = cancellations(timeline);
    expect(cxs.some(e => e.sourceNodeId === 'root' && e.targetNodeId === 'sibling')).toBe(true);
  });

  // ── Test 13: the root coroutine itself fails ────────────────────────
  it('13. failing root cancels itself and its descendants, no upward propagation', () => {
    const tree = root([
      node('child', 'launch'),
    ]);
    // Attach a failure to the root itself.
    tree.failure = { exceptionMessage: 'root boom', timingMs: 300 };

    const timeline = generateTimeline(tree, 'Root failure');

    assertValid(timeline);

    expect(finalState(timeline, 'root')).toBe('Cancelled');
    expect(finalState(timeline, 'child')).toBe('Cancelled');

    // No exception can propagate above the root.
    const exs = exceptions(timeline);
    expect(exs.every(e => e.sourceNodeId !== 'root')).toBe(true);
    // The root's failure cancels its descendant downward.
    const cxs = cancellations(timeline);
    expect(cxs.some(e => e.sourceNodeId === 'root' && e.targetNodeId === 'child')).toBe(true);
  });

  // ── Test 14: two independent failures in separate subtrees ──────────
  it('14. two sibling subtrees each failing both cascade to the root', () => {
    const tree = root([
      node('left', 'launch L', 'Launch', 'Job', [
        node('l1', 'launch L1 (fails)', 'Launch', 'Job', [], {
          exceptionMessage: 'L boom', timingMs: 700,
        }),
      ]),
      node('right', 'launch R', 'Launch', 'Job', [
        node('r1', 'launch R1 (fails)', 'Launch', 'Job', [], {
          exceptionMessage: 'R boom', timingMs: 700,
        }),
      ]),
    ]);
    const timeline = generateTimeline(tree, 'Double failure');

    assertValid(timeline);

    // Everything ends Cancelled — the first failure to reach the root cancels
    // the whole tree; the second failing node is already on its way down.
    for (const id of ['l1', 'left', 'r1', 'right', 'root']) {
      expect(finalState(timeline, id)).toBe('Cancelled');
    }
    // At least one failure chain reaches the root.
    const exs = exceptions(timeline);
    expect(exs.some(e => e.targetNodeId === 'root')).toBe(true);
  });
});
