import { useState, useCallback } from 'react';
import { BuilderNodeConfig } from '../builder/types';

let nextId = 1;
function genId(): string {
  return `builder-node-${nextId++}`;
}

function createDefaultNode(parentBuilder?: string): BuilderNodeConfig {
  return {
    id: genId(),
    displayName: `coroutine-${nextId - 1}`,
    builder: 'Launch',
    jobType: 'Job',
    children: [],
  };
}

function createDefaultRoot(): BuilderNodeConfig {
  return {
    id: genId(),
    displayName: 'root',
    builder: 'RunBlocking',
    jobType: 'Job',
    children: [],
  };
}

// Deep-update a node in the tree by id
function updateNodeInTree(
  root: BuilderNodeConfig,
  nodeId: string,
  updater: (node: BuilderNodeConfig) => BuilderNodeConfig
): BuilderNodeConfig {
  if (root.id === nodeId) return updater(root);
  return {
    ...root,
    children: root.children.map(c => updateNodeInTree(c, nodeId, updater)),
  };
}

// Add a child to a specific parent node
function addChildToNode(root: BuilderNodeConfig, parentId: string): BuilderNodeConfig {
  return updateNodeInTree(root, parentId, node => ({
    ...node,
    children: [...node.children, createDefaultNode(node.builder)],
  }));
}

// Remove a node (and its subtree) from the tree
function removeNodeFromTree(root: BuilderNodeConfig, nodeId: string): BuilderNodeConfig | null {
  if (root.id === nodeId) return null;
  return {
    ...root,
    children: root.children
      .map(c => removeNodeFromTree(c, nodeId))
      .filter((c): c is BuilderNodeConfig => c !== null),
  };
}

export interface BuilderState {
  root: BuilderNodeConfig;
  selectedNodeId: string | null;
}

export interface BuilderActions {
  addChild: (parentId: string) => void;
  removeNode: (nodeId: string) => void;
  updateNode: (nodeId: string, updates: Partial<BuilderNodeConfig>) => void;
  selectNode: (nodeId: string | null) => void;
  reset: () => void;
  loadTree: (root: BuilderNodeConfig) => void;
}

export function useBuilderState(): [BuilderState, BuilderActions] {
  const [root, setRoot] = useState<BuilderNodeConfig>(createDefaultRoot);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  const addChild = useCallback((parentId: string) => {
    setRoot(prev => addChildToNode(prev, parentId));
  }, []);

  const removeNode = useCallback((nodeId: string) => {
    setRoot(prev => {
      const result = removeNodeFromTree(prev, nodeId);
      return result ?? createDefaultRoot();
    });
    setSelectedNodeId(prev => prev === nodeId ? null : prev);
  }, []);

  const updateNode = useCallback((nodeId: string, updates: Partial<BuilderNodeConfig>) => {
    setRoot(prev => updateNodeInTree(prev, nodeId, node => ({ ...node, ...updates })));
  }, []);

  const selectNode = useCallback((nodeId: string | null) => {
    setSelectedNodeId(nodeId);
  }, []);

  const reset = useCallback(() => {
    nextId = 1;
    setRoot(createDefaultRoot());
    setSelectedNodeId(null);
  }, []);

  const loadTree = useCallback((tree: BuilderNodeConfig) => {
    // Find max numeric suffix in all node IDs to avoid collisions
    let maxId = 0;
    const scan = (node: BuilderNodeConfig) => {
      const match = node.id.match(/builder-node-(\d+)/);
      if (match) maxId = Math.max(maxId, parseInt(match[1], 10));
      node.children.forEach(scan);
    };
    scan(tree);
    nextId = maxId + 1;
    setRoot(tree);
    setSelectedNodeId(null);
  }, []);

  return [
    { root, selectedNodeId },
    { addChild, removeNode, updateNode, selectNode, reset, loadTree },
  ];
}
