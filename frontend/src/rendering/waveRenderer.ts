import { LayoutNode } from '../types';
import { CANCELLATION_WAVE_COLOR, EXCEPTION_WAVE_COLOR } from '../utils/colors';

const NODE_RADIUS = 24;
const WAVE_RADIUS = 6;

export interface WaveAnimation {
  id: string;
  type: 'cancellation' | 'exception';
  fromNodeId: string;
  toNodeId: string;
  startTime: number;
  duration: number;
}

export function drawWaves(
  ctx: CanvasRenderingContext2D,
  waves: WaveAnimation[],
  nodeMap: Map<string, LayoutNode>,
  now: number
): void {
  for (const wave of waves) {
    const fromNode = nodeMap.get(wave.fromNodeId);
    const toNode = nodeMap.get(wave.toNodeId);
    if (!fromNode || !toNode) continue;

    const elapsed = now - wave.startTime;
    if (elapsed < 0 || elapsed > wave.duration) continue;

    const t = elapsed / wave.duration;
    const color = wave.type === 'cancellation' ? CANCELLATION_WAVE_COLOR : EXCEPTION_WAVE_COLOR;

    // Interpolate position along edge
    const startY = fromNode.y + NODE_RADIUS;
    const endY = toNode.y - NODE_RADIUS;
    const midY = (startY + endY) / 2;

    // Approximate bezier at parameter t
    const oneMinusT = 1 - t;
    const px = oneMinusT * oneMinusT * oneMinusT * fromNode.x
      + 3 * oneMinusT * oneMinusT * t * fromNode.x
      + 3 * oneMinusT * t * t * toNode.x
      + t * t * t * toNode.x;
    const py = oneMinusT * oneMinusT * oneMinusT * startY
      + 3 * oneMinusT * oneMinusT * t * midY
      + 3 * oneMinusT * t * t * midY
      + t * t * t * endY;

    // Glow
    ctx.save();
    ctx.shadowColor = color;
    ctx.shadowBlur = 12;

    ctx.beginPath();
    ctx.arc(px, py, WAVE_RADIUS, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();

    // Trail
    ctx.globalAlpha = 0.3;
    for (let i = 1; i <= 3; i++) {
      const trailT = Math.max(0, t - i * 0.05);
      const oneMinusTT = 1 - trailT;
      const tx = oneMinusTT * oneMinusTT * oneMinusTT * fromNode.x
        + 3 * oneMinusTT * oneMinusTT * trailT * fromNode.x
        + 3 * oneMinusTT * trailT * trailT * toNode.x
        + trailT * trailT * trailT * toNode.x;
      const ty = oneMinusTT * oneMinusTT * oneMinusTT * startY
        + 3 * oneMinusTT * oneMinusTT * trailT * midY
        + 3 * oneMinusTT * trailT * trailT * midY
        + trailT * trailT * trailT * endY;

      ctx.beginPath();
      ctx.arc(tx, ty, WAVE_RADIUS - i, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();
  }
}
