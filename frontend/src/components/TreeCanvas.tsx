import React, { useRef, useEffect, useCallback, useState } from 'react';
import { LayoutNode } from '../types';
import { drawNode, hitTestNode, NodeAnimation } from '../rendering/nodeRenderer';
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
}

export const TreeCanvas: React.FC<Props> = ({
  layoutRoot, secondLayoutRoot, nodeAnimations, waveAnimations, selectedNodeId, onNodeClick,
  onNodeRightClick, hoveredNodeId, loadCounter,
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

  // Auto-center tree on load
  useEffect(() => {
    if (!layoutRoot || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const w = rect.width || canvas.width;
    const h = rect.height || canvas.height;
    if (w === 0 || h === 0) return;

    const bounds = getTreeBounds(layoutRoot);
    let maxX = bounds.maxX;
    let maxY = bounds.maxY;
    if (secondLayoutRoot) {
      const b2 = getTreeBounds(secondLayoutRoot);
      maxX = Math.max(maxX, b2.maxX);
      maxY = Math.max(maxY, b2.maxY);
    }
    const treeWidth = maxX - bounds.minX + 100;
    const treeHeight = maxY - bounds.minY + 150;
    const scaleX = w / treeWidth;
    const scaleY = h / treeHeight;
    const newZoom = Math.min(scaleX, scaleY, 1.5);
    const centerX = (w / newZoom - treeWidth) / 2 - bounds.minX + 50;
    const centerY = 20;
    setZoom(newZoom);
    setPan({ x: centerX, y: centerY });
  }, [layoutRoot, secondLayoutRoot, loadCounter]);

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

      ctx.restore();

      animFrameRef.current = requestAnimationFrame(render);
    };

    animFrameRef.current = requestAnimationFrame(render);

    return () => {
      running = false;
      cancelAnimationFrame(animFrameRef.current);
    };
  }, [layoutRoot, secondLayoutRoot, nodeAnimations, waveAnimations, selectedNodeId, pan, zoom, getAllNodes, buildNodeMap]);

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
