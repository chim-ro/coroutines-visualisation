import { LayoutNode } from '../types';
import { STATE_COLORS, TEXT_COLOR } from '../utils/colors';

const NODE_RADIUS = 24;
const LABEL_OFFSET = 36;

export interface NodeAnimation {
  nodeId: string;
  type: 'flash' | 'breathing' | 'flicker';
  startTime: number;
  duration: number;
  color?: string;
}

export function drawNode(
  ctx: CanvasRenderingContext2D,
  node: LayoutNode,
  isSelected: boolean,
  animations: NodeAnimation[],
  now: number
): void {
  const baseColor = STATE_COLORS[node.state];
  const nodeAnims = animations.filter(a => a.nodeId === node.id);

  ctx.save();

  // Breathing animation for Active nodes
  let scale = 1;
  const breathingAnim = nodeAnims.find(a => a.type === 'breathing');
  if (breathingAnim || node.state === 'Active') {
    const t = (now % 2000) / 2000;
    scale = 1 + Math.sin(t * Math.PI * 2) * 0.05;
  }

  // Slow pulse for Suspended nodes (sleeping/waiting)
  if (node.state === 'Suspended') {
    const t = (now % 3000) / 3000;
    scale = 1 + Math.sin(t * Math.PI * 2) * 0.03;
  }

  // Flicker for Cancelling nodes
  let alpha = 1;
  if (node.state === 'Cancelling') {
    const t = (now % 500) / 500;
    alpha = 0.5 + Math.sin(t * Math.PI * 2) * 0.4;
  }

  // Dim pulsing for Suspended nodes
  if (node.state === 'Suspended') {
    const t = (now % 3000) / 3000;
    alpha = 0.5 + Math.sin(t * Math.PI * 2) * 0.2;
  }

  // Flash animation
  const flashAnim = nodeAnims.find(a => a.type === 'flash');
  let flashRing = 0;
  if (flashAnim) {
    const elapsed = now - flashAnim.startTime;
    if (elapsed < flashAnim.duration) {
      const t = elapsed / flashAnim.duration;
      flashRing = (1 - t) * 12;
      scale = 1 + (1 - t) * 0.2;
    }
  }

  ctx.globalAlpha = alpha;
  ctx.translate(node.x, node.y);
  ctx.scale(scale, scale);

  // Flash ring
  if (flashRing > 0) {
    ctx.beginPath();
    ctx.arc(0, 0, NODE_RADIUS + flashRing, 0, Math.PI * 2);
    ctx.strokeStyle = baseColor;
    ctx.lineWidth = 2;
    ctx.globalAlpha = alpha * (flashRing / 12) * 0.5;
    ctx.stroke();
    ctx.globalAlpha = alpha;
  }

  // Node circle
  ctx.beginPath();
  ctx.arc(0, 0, NODE_RADIUS, 0, Math.PI * 2);
  ctx.fillStyle = baseColor + '33'; // 20% opacity fill
  ctx.fill();
  ctx.strokeStyle = baseColor;
  ctx.lineWidth = isSelected ? 3 : 2;
  ctx.stroke();

  // Selection ring
  if (isSelected) {
    ctx.beginPath();
    ctx.arc(0, 0, NODE_RADIUS + 5, 0, Math.PI * 2);
    ctx.strokeStyle = '#ffffff44';
    ctx.lineWidth = 2;
    ctx.stroke();
  }

  // Builder type icon (first letter)
  ctx.fillStyle = baseColor;
  ctx.font = 'bold 14px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const icon = node.builder === 'Launch' ? 'L'
    : node.builder === 'Async' ? 'A'
    : node.builder === 'CoroutineScope' ? 'CS'
    : node.builder === 'SupervisorScope' ? 'SS'
    : 'RB';
  ctx.fillText(icon, 0, 0);

  // Label below node
  ctx.fillStyle = TEXT_COLOR;
  ctx.font = '11px monospace';
  ctx.fillText(node.displayName, 0, LABEL_OFFSET);

  // State label
  ctx.fillStyle = baseColor;
  ctx.font = '10px monospace';
  ctx.fillText(node.state, 0, LABEL_OFFSET + 14);

  // SupervisorJob badge
  if (node.jobType === 'SupervisorJob') {
    ctx.fillStyle = '#ff9e64';
    ctx.font = 'bold 8px monospace';
    ctx.fillText('SJ', NODE_RADIUS - 4, -NODE_RADIUS + 4);
  }

  // Suspended "zzz" indicator
  if (node.state === 'Suspended') {
    const t = (now % 3000) / 3000;
    ctx.fillStyle = baseColor;
    ctx.font = 'bold 9px monospace';
    ctx.textAlign = 'left';
    const yOff = Math.sin(t * Math.PI * 2) * 3;
    ctx.globalAlpha = 0.6 + Math.sin(t * Math.PI * 2) * 0.3;
    ctx.fillText('z', NODE_RADIUS + 2, -NODE_RADIUS + 2 + yOff);
    ctx.font = 'bold 11px monospace';
    ctx.fillText('z', NODE_RADIUS + 9, -NODE_RADIUS - 4 + yOff * 0.7);
    ctx.font = 'bold 13px monospace';
    ctx.fillText('z', NODE_RADIUS + 18, -NODE_RADIUS - 10 + yOff * 0.4);
    ctx.textAlign = 'center';
  }

  ctx.restore();
}

export function hitTestNode(node: LayoutNode, mx: number, my: number): boolean {
  const dx = mx - node.x;
  const dy = my - node.y;
  return dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS;
}

export { NODE_RADIUS };
