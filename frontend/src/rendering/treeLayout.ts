import { CoroutineNode, LayoutNode, JobState } from '../types';

const NODE_SPACING_X = 130;
const LEVEL_HEIGHT = 100;

interface TempNode {
  id: string;
  displayName: string;
  builder: CoroutineNode['builder'];
  jobType: CoroutineNode['jobType'];
  state: JobState;
  children: TempNode[];
  x: number;
  y: number;
  mod: number;
  thread: TempNode | null;
  ancestor: TempNode;
  prelim: number;
  change: number;
  shift: number;
  number: number;
  parent: TempNode | null;
}

function buildTempTree(node: CoroutineNode, depth: number, parent: TempNode | null, number: number): TempNode {
  const temp: TempNode = {
    id: node.id,
    displayName: node.displayName,
    builder: node.builder,
    jobType: node.jobType,
    state: node.initialState,
    children: [],
    x: 0,
    y: depth * LEVEL_HEIGHT,
    mod: 0,
    thread: null,
    ancestor: null!,
    prelim: 0,
    change: 0,
    shift: 0,
    number,
    parent,
  };
  temp.ancestor = temp;
  temp.children = node.children.map((child, i) => buildTempTree(child, depth + 1, temp, i + 1));
  return temp;
}

function firstWalk(v: TempNode): void {
  if (v.children.length === 0) {
    if (v.number > 1 && v.parent) {
      const leftSibling = v.parent.children[v.number - 2];
      v.prelim = leftSibling.prelim + NODE_SPACING_X;
    }
  } else {
    let defaultAncestor = v.children[0];
    for (const w of v.children) {
      firstWalk(w);
      defaultAncestor = apportion(w, defaultAncestor);
    }
    executeShifts(v);
    const midpoint = (v.children[0].prelim + v.children[v.children.length - 1].prelim) / 2;
    if (v.number > 1 && v.parent) {
      const leftSibling = v.parent.children[v.number - 2];
      v.prelim = leftSibling.prelim + NODE_SPACING_X;
      v.mod = v.prelim - midpoint;
    } else {
      v.prelim = midpoint;
    }
  }
}

function secondWalk(v: TempNode, m: number): void {
  v.x = v.prelim + m;
  for (const w of v.children) {
    secondWalk(w, m + v.mod);
  }
}

function apportion(v: TempNode, defaultAncestor: TempNode): TempNode {
  if (v.number > 1 && v.parent) {
    const w = v.parent.children[v.number - 2];
    let vInnerRight: TempNode | null = v;
    let vOuterRight: TempNode | null = v;
    let vInnerLeft: TempNode | null = w;
    let vOuterLeft: TempNode | null = v.parent.children[0];
    let sInnerRight = v.mod;
    let sOuterRight = v.mod;
    let sInnerLeft = w.mod;
    let sOuterLeft = vOuterLeft.mod;

    while (nextRight(vInnerLeft) && nextLeft(vInnerRight)) {
      vInnerLeft = nextRight(vInnerLeft)!;
      vInnerRight = nextLeft(vInnerRight)!;
      vOuterLeft = nextLeft(vOuterLeft)!;
      vOuterRight = nextRight(vOuterRight)!;
      vOuterRight.ancestor = v;
      const shift = (vInnerLeft.prelim + sInnerLeft) - (vInnerRight.prelim + sInnerRight) + NODE_SPACING_X;
      if (shift > 0) {
        moveSubtree(ancestor(vInnerLeft, v, defaultAncestor), v, shift);
        sInnerRight += shift;
        sOuterRight += shift;
      }
      sInnerLeft += vInnerLeft.mod;
      sInnerRight += vInnerRight.mod;
      sOuterLeft += vOuterLeft!.mod;
      sOuterRight += vOuterRight.mod;
    }
    if (nextRight(vInnerLeft) && !nextRight(vOuterRight)) {
      vOuterRight.thread = nextRight(vInnerLeft);
      vOuterRight.mod += sInnerLeft - sOuterRight;
    }
    if (nextLeft(vInnerRight) && !nextLeft(vOuterLeft!)) {
      vOuterLeft!.thread = nextLeft(vInnerRight);
      vOuterLeft!.mod += sInnerRight - sOuterLeft;
      defaultAncestor = v;
    }
  }
  return defaultAncestor;
}

function nextLeft(v: TempNode | null): TempNode | null {
  if (!v) return null;
  return v.children.length > 0 ? v.children[0] : v.thread;
}

function nextRight(v: TempNode | null): TempNode | null {
  if (!v) return null;
  return v.children.length > 0 ? v.children[v.children.length - 1] : v.thread;
}

function moveSubtree(wl: TempNode, wr: TempNode, shift: number): void {
  const subtrees = wr.number - wl.number;
  if (subtrees > 0) {
    wr.change -= shift / subtrees;
    wr.shift += shift;
    wl.change += shift / subtrees;
    wr.prelim += shift;
    wr.mod += shift;
  }
}

function executeShifts(v: TempNode): void {
  let shift = 0;
  let change = 0;
  for (let i = v.children.length - 1; i >= 0; i--) {
    const w = v.children[i];
    w.prelim += shift;
    w.mod += shift;
    change += w.change;
    shift += w.shift + change;
  }
}

function ancestor(vil: TempNode, v: TempNode, defaultAncestor: TempNode): TempNode {
  if (vil.ancestor.parent === v.parent) return vil.ancestor;
  return defaultAncestor;
}

function toLayoutNode(temp: TempNode, parent: LayoutNode | null): LayoutNode {
  const node: LayoutNode = {
    id: temp.id,
    displayName: temp.displayName,
    builder: temp.builder,
    jobType: temp.jobType,
    state: temp.state,
    x: temp.x,
    y: temp.y,
    children: [],
    parent,
  };
  node.children = temp.children.map(c => toLayoutNode(c, node));
  return node;
}

export function layoutTree(root: CoroutineNode, offsetX: number = 0, offsetY: number = 50): LayoutNode {
  const temp = buildTempTree(root, 0, null, 1);
  firstWalk(temp);
  secondWalk(temp, 0);

  // Normalize: find min x and shift so leftmost is at padding
  let minX = Infinity;
  const collectMinX = (n: TempNode) => {
    if (n.x < minX) minX = n.x;
    n.children.forEach(collectMinX);
  };
  collectMinX(temp);

  const shiftAll = (n: TempNode, dx: number, dy: number) => {
    n.x += dx;
    n.y += dy;
    n.children.forEach(c => shiftAll(c, dx, dy));
  };
  shiftAll(temp, -minX + offsetX, offsetY);

  return toLayoutNode(temp, null);
}

export function getTreeBounds(root: LayoutNode): { minX: number; maxX: number; minY: number; maxY: number } {
  let minX = root.x, maxX = root.x, minY = root.y, maxY = root.y;
  const visit = (n: LayoutNode) => {
    if (n.x < minX) minX = n.x;
    if (n.x > maxX) maxX = n.x;
    if (n.y < minY) minY = n.y;
    if (n.y > maxY) maxY = n.y;
    n.children.forEach(visit);
  };
  visit(root);
  return { minX, maxX, minY, maxY };
}

export function flattenTree(root: LayoutNode): LayoutNode[] {
  const result: LayoutNode[] = [];
  const visit = (n: LayoutNode) => {
    result.push(n);
    n.children.forEach(visit);
  };
  visit(root);
  return result;
}
