import React, { useRef, useEffect, useCallback } from 'react';
import { BuilderNodeConfig } from './types';
import { CoroutineNode, LayoutNode } from '../types';
import { layoutTree, flattenTree } from '../rendering/treeLayout';
import { drawNode } from '../rendering/nodeRenderer';
import { drawEdges } from '../rendering/edgeRenderer';
import { BG_COLOR, BORDER_COLOR, ACCENT_COLOR } from '../utils/colors';

interface Props {
  root: BuilderNodeConfig;
  selectedNodeId: string | null;
  onNodeClick: (nodeId: string) => void;
  width?: number;
  height?: number;
}

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

export const BuilderTreePreview: React.FC<Props> = ({
  root,
  selectedNodeId,
  onNodeClick,
  width = 400,
  height = 300,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const layoutRef = useRef<LayoutNode | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = devicePixelRatio;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = BG_COLOR;
    ctx.fillRect(0, 0, width, height);

    const coroutineRoot = toCoroutineNode(root);
    const layoutRoot = layoutTree(coroutineRoot, 30, 30);
    layoutRef.current = layoutRoot;

    // Auto-scale to fit
    const nodes = flattenTree(layoutRoot);
    if (nodes.length === 0) return;

    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (const n of nodes) {
      minX = Math.min(minX, n.x);
      maxX = Math.max(maxX, n.x);
      minY = Math.min(minY, n.y);
      maxY = Math.max(maxY, n.y);
    }

    const treeWidth = maxX - minX + 100;
    const treeHeight = maxY - minY + 100;
    const scale = Math.min(width / treeWidth, height / treeHeight, 1.2);
    const offsetX = (width / scale - treeWidth) / 2 - minX + 50;
    const offsetY = (height / scale - treeHeight) / 2 - minY + 50;

    ctx.save();
    ctx.scale(scale, scale);
    ctx.translate(offsetX, offsetY);

    drawEdges(ctx, layoutRoot);
    const now = performance.now();
    for (const node of nodes) {
      // Highlight failing nodes with a subtle indicator
      drawNode(ctx, node, node.id === selectedNodeId, [], now);
    }

    ctx.restore();
  }, [root, selectedNodeId, width, height]);

  const handleClick = useCallback((e: React.MouseEvent) => {
    const canvas = canvasRef.current;
    const layout = layoutRef.current;
    if (!canvas || !layout) return;

    const rect = canvas.getBoundingClientRect();
    const nodes = flattenTree(layout);

    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (const n of nodes) {
      minX = Math.min(minX, n.x);
      maxX = Math.max(maxX, n.x);
      minY = Math.min(minY, n.y);
      maxY = Math.max(maxY, n.y);
    }

    const treeWidth = maxX - minX + 100;
    const treeHeight = maxY - minY + 100;
    const scale = Math.min(width / treeWidth, height / treeHeight, 1.2);
    const offsetX = (width / scale - treeWidth) / 2 - minX + 50;
    const offsetY = (height / scale - treeHeight) / 2 - minY + 50;

    const mx = (e.clientX - rect.left) / scale - offsetX;
    const my = (e.clientY - rect.top) / scale - offsetY;

    for (const node of nodes) {
      const dx = mx - node.x;
      const dy = my - node.y;
      if (dx * dx + dy * dy <= 24 * 24) {
        onNodeClick(node.id);
        return;
      }
    }
  }, [width, height, onNodeClick]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        width,
        height,
        border: `1px solid ${BORDER_COLOR}`,
        borderRadius: 8,
        cursor: 'pointer',
      }}
      onClick={handleClick}
    />
  );
};
