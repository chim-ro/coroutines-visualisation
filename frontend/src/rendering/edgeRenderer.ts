import { LayoutNode } from '../types';
import { BORDER_COLOR, STATE_COLORS } from '../utils/colors';

const ARROW_SIZE = 6;
const NODE_RADIUS = 24;

export function drawEdges(ctx: CanvasRenderingContext2D, root: LayoutNode): void {
  const visit = (node: LayoutNode) => {
    for (const child of node.children) {
      drawEdge(ctx, node, child);
      visit(child);
    }
  };
  visit(root);
}

function drawEdge(ctx: CanvasRenderingContext2D, parent: LayoutNode, child: LayoutNode): void {
  const startY = parent.y + NODE_RADIUS;
  const endY = child.y - NODE_RADIUS;
  const midY = (startY + endY) / 2;

  ctx.beginPath();
  ctx.moveTo(parent.x, startY);
  ctx.bezierCurveTo(parent.x, midY, child.x, midY, child.x, endY);
  ctx.strokeStyle = BORDER_COLOR;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // Arrowhead
  const angle = Math.atan2(endY - midY, child.x - child.x) || Math.PI / 2;
  ctx.save();
  ctx.translate(child.x, endY);
  ctx.rotate(angle);
  ctx.beginPath();
  ctx.moveTo(0, 0);
  ctx.lineTo(-ARROW_SIZE, -ARROW_SIZE);
  ctx.moveTo(0, 0);
  ctx.lineTo(ARROW_SIZE, -ARROW_SIZE);
  ctx.strokeStyle = BORDER_COLOR;
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.restore();
}
