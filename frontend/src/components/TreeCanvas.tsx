import React, { useRef, useEffect, useCallback, useState } from 'react';
import { LayoutNode } from '../types';
import {
  drawNode, hitTestNode, NodeAnimation,
  NODE_VISUAL_HALF_WIDTH, NODE_VISUAL_TOP, NODE_VISUAL_BOTTOM,
} from '../rendering/nodeRenderer';
import { drawEdges } from '../rendering/edgeRenderer';
import { drawWaves, WaveAnimation } from '../rendering/waveRenderer';
import { flattenTree, getTreeBounds } from '../rendering/treeLayout';
import { BG_COLOR, TEXT_DIM } from '../utils/colors';

interface Props {
  layoutRoot: LayoutNode | null;
  secondLayoutRoot: LayoutNode | null;
  nodeAnimations: NodeAnimation[];
  waveAnimations: WaveAnimation[];
  selectedNodeId: string | null;
  onNodeClick: (nodeId: string) => void;
  onNodeRightClick?: (nodeId: string, screenX: number, screenY: number) => void;
  hoveredNodeId?: string | null;
  loadCounter?: number;
  diffHighlightNodes?: Set<string>;
  leftLabel?: string;
  rightLabel?: string;
}

export const TreeCanvas: React.FC<Props> = ({
  layoutRoot, secondLayoutRoot, nodeAnimations, waveAnimations, selectedNodeId, onNodeClick,
  onNodeRightClick, hoveredNodeId: _hoveredNodeId, loadCounter, diffHighlightNodes, leftLabel, rightLabel,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [isDragging, setIsDragging] = useState(false);
  const lastMouseRef = useRef({ x: 0, y: 0 });
  const animFrameRef = useRef<number>(0);

  const getAllNodes = useCallback((): LayoutNode[] => {
    const nodes: LayoutNode[] = [];
    if (layoutRoot) nodes.push(...flattenTree(layoutRoot));
    if (secondLayoutRoot) nodes.push(...flattenTree(secondLayoutRoot));
    return nodes;
  }, [layoutRoot, secondLayoutRoot]);

  const buildNodeMap = useCallback((): Map<string, LayoutNode> => {
    const map = new Map<string, LayoutNode>();
    for (const node of getAllNodes()) {
      map.set(node.id, node);
    }
    return map;
  }, [getAllNodes]);

  // Auto-center tree on load. Bounds account for the visible extent of each
  // node (including its wrapped label below), not just node centers, so
  // wrapped/long labels at the edges don't get clipped.
  useEffect(() => {
    if (!layoutRoot || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const w = rect.width || canvas.width;
    const h = rect.height || canvas.height;
    if (w === 0 || h === 0) return;

    const renderableBounds = (root: LayoutNode) => {
      const b = getTreeBounds(root);
      return {
        minX: b.minX - NODE_VISUAL_HALF_WIDTH,
        maxX: b.maxX + NODE_VISUAL_HALF_WIDTH,
        minY: b.minY - NODE_VISUAL_TOP,
        maxY: b.maxY + NODE_VISUAL_BOTTOM,
      };
    };

    const b1 = renderableBounds(layoutRoot);
    let { minX, maxX, minY, maxY } = b1;
    if (secondLayoutRoot) {
      const b2 = renderableBounds(secondLayoutRoot);
      minX = Math.min(minX, b2.minX);
      maxX = Math.max(maxX, b2.maxX);
      minY = Math.min(minY, b2.minY);
      maxY = Math.max(maxY, b2.maxY);
    }

    // Reserve vertical headroom for tree labels drawn above each tree root.
    const labelPadding = (leftLabel || rightLabel) ? 30 : 0;
    minY -= labelPadding;

    const treeWidth = maxX - minX;
    const treeHeight = maxY - minY;

    const scaleX = w / treeWidth;
    const scaleY = h / treeHeight;
    const newZoom = Math.min(scaleX, scaleY, 1.5);

    // Center the renderable region in the viewport.
    const centerX = (w / newZoom - treeWidth) / 2 - minX;
    const centerY = (h / newZoom - treeHeight) / 2 - minY;

    setZoom(newZoom);
    setPan({ x: centerX, y: centerY });
  }, [layoutRoot, secondLayoutRoot, loadCounter, leftLabel, rightLabel]);

  // Render loop
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let running = true;

    const render = () => {
      if (!running) return;
      const now = performance.now();

      // Resize canvas to container
      const rect = canvas.getBoundingClientRect();
      if (canvas.width !== rect.width * devicePixelRatio || canvas.height !== rect.height * devicePixelRatio) {
        canvas.width = rect.width * devicePixelRatio;
        canvas.height = rect.height * devicePixelRatio;
        ctx.scale(devicePixelRatio, devicePixelRatio);
      }

      ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
      ctx.clearRect(0, 0, rect.width, rect.height);
      ctx.fillStyle = BG_COLOR;
      ctx.fillRect(0, 0, rect.width, rect.height);

      if (!layoutRoot) {
        ctx.fillStyle = TEXT_DIM;
        ctx.font = '16px monospace';
        ctx.textAlign = 'center';
        ctx.fillText('Select a scenario from the left panel', rect.width / 2, rect.height / 2);
        animFrameRef.current = requestAnimationFrame(render);
        return;
      }

      ctx.save();
      ctx.translate(pan.x * zoom, pan.y * zoom);
      ctx.scale(zoom, zoom);

      const nodeMap = buildNodeMap();

      // Draw edges
      drawEdges(ctx, layoutRoot);
      if (secondLayoutRoot) {
        // Draw divider
        const b1 = getTreeBounds(layoutRoot);
        const b2 = getTreeBounds(secondLayoutRoot);
        const divX = (b1.maxX + b2.minX) / 2;
        ctx.save();
        ctx.strokeStyle = TEXT_DIM;
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(divX, 0);
        ctx.lineTo(divX, Math.max(b1.maxY, b2.maxY) + 80);
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.restore();

        drawEdges(ctx, secondLayoutRoot);
      }

      // Draw waves
      drawWaves(ctx, waveAnimations, nodeMap, now);

      // Draw nodes
      for (const node of getAllNodes()) {
        drawNode(ctx, node, node.id === selectedNodeId, nodeAnimations, now);
      }

      // Draw diff highlight borders
      if (diffHighlightNodes && diffHighlightNodes.size > 0) {
        for (const node of getAllNodes()) {
          if (diffHighlightNodes.has(node.id)) {
            ctx.save();
            ctx.strokeStyle = '#ff9e64';
            ctx.lineWidth = 3;
            ctx.setLineDash([6, 4]);
            ctx.beginPath();
            ctx.roundRect(node.x - 55, node.y - 25, 110, 50, 8);
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.restore();
          }
        }
      }

      // Draw tree labels
      if (leftLabel && layoutRoot) {
        const b = getTreeBounds(layoutRoot);
        ctx.save();
        ctx.fillStyle = TEXT_DIM;
        ctx.font = 'bold 13px monospace';
        ctx.textAlign = 'center';
        ctx.fillText(leftLabel, (b.minX + b.maxX) / 2, b.minY - 35);
        ctx.restore();
      }
      if (rightLabel && secondLayoutRoot) {
        const b = getTreeBounds(secondLayoutRoot);
        ctx.save();
        ctx.fillStyle = TEXT_DIM;
        ctx.font = 'bold 13px monospace';
        ctx.textAlign = 'center';
        ctx.fillText(rightLabel, (b.minX + b.maxX) / 2, b.minY - 35);
        ctx.restore();
      }

      ctx.restore();

      animFrameRef.current = requestAnimationFrame(render);
    };

    animFrameRef.current = requestAnimationFrame(render);

    return () => {
      running = false;
      cancelAnimationFrame(animFrameRef.current);
    };
  }, [layoutRoot, secondLayoutRoot, nodeAnimations, waveAnimations, selectedNodeId, pan, zoom, getAllNodes, buildNodeMap, diffHighlightNodes, leftLabel, rightLabel]);

  // Mouse handlers
  const handleMouseDown = (e: React.MouseEvent) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / zoom - pan.x;
    const my = (e.clientY - rect.top) / zoom - pan.y;

    // Check node clicks
    for (const node of getAllNodes()) {
      if (hitTestNode(node, mx, my)) {
        onNodeClick(node.id);
        return;
      }
    }

    setIsDragging(true);
    lastMouseRef.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    const dx = (e.clientX - lastMouseRef.current.x) / zoom;
    const dy = (e.clientY - lastMouseRef.current.y) / zoom;
    setPan(p => ({ x: p.x + dx, y: p.y + dy }));
    lastMouseRef.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseUp = () => setIsDragging(false);

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const factor = e.deltaY > 0 ? 0.9 : 1.1;
    setZoom(z => Math.min(Math.max(z * factor, 0.3), 3));
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    if (!onNodeRightClick) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / zoom - pan.x;
    const my = (e.clientY - rect.top) / zoom - pan.y;

    for (const node of getAllNodes()) {
      if (hitTestNode(node, mx, my)) {
        e.preventDefault();
        onNodeRightClick(node.id, e.clientX, e.clientY);
        return;
      }
    }
  };

  return (
    <canvas
      ref={canvasRef}
      style={{ width: '100%', height: '100%', cursor: isDragging ? 'grabbing' : 'grab' }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
      onWheel={handleWheel}
      onContextMenu={handleContextMenu}
    />
  );
};
