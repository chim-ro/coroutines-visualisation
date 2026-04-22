import React from 'react';
import { LayoutNode, SimulationEvent } from '../types';
import { STATE_COLORS, TEXT_DIM, TEXT_COLOR, BORDER_COLOR, ACCENT_COLOR } from '../utils/colors';
import { flattenTree } from '../rendering/treeLayout';

interface Props {
  layoutRoot: LayoutNode | null;
  secondLayoutRoot: LayoutNode | null;
  selectedNodeId: string | null;
  eventLog: SimulationEvent[];
  nodeStates: Map<string, import('../types').JobState>;
}

export const InfoPanel: React.FC<Props> = ({ layoutRoot, secondLayoutRoot, selectedNodeId, eventLog, nodeStates }) => {
  let selectedNode: LayoutNode | null = null;
  if (selectedNodeId) {
    const allNodes = [
      ...(layoutRoot ? flattenTree(layoutRoot) : []),
      ...(secondLayoutRoot ? flattenTree(secondLayoutRoot) : []),
    ];
    selectedNode = allNodes.find(n => n.id === selectedNodeId) ?? null;
  }

  const currentState = selectedNodeId ? nodeStates.get(selectedNodeId) : null;

  return (
    <div>
      {selectedNode && (
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: 11, color: TEXT_DIM, letterSpacing: 2, marginBottom: 12 }}>SELECTED NODE</h3>
          <div style={{ fontSize: 12, marginBottom: 4 }}>
            <span style={{ color: TEXT_DIM }}>Name: </span>
            <span>{selectedNode.displayName}</span>
          </div>
          <div style={{ fontSize: 12, marginBottom: 4 }}>
            <span style={{ color: TEXT_DIM }}>Builder: </span>
            <span>{selectedNode.builder}</span>
          </div>
          <div style={{ fontSize: 12, marginBottom: 4 }}>
            <span style={{ color: TEXT_DIM }}>Job: </span>
            <span>{selectedNode.jobType}</span>
          </div>
          <div style={{ fontSize: 12, marginBottom: 4 }}>
            <span style={{ color: TEXT_DIM }}>State: </span>
            <span style={{ color: currentState ? STATE_COLORS[currentState] : TEXT_COLOR }}>
              {currentState ?? selectedNode.state}
            </span>
          </div>
        </div>
      )}

      <h3 style={{ fontSize: 11, color: TEXT_DIM, letterSpacing: 2, marginBottom: 12 }}>EVENT LOG</h3>
      <div style={{ maxHeight: 300, overflowY: 'auto' }}>
        {eventLog.length === 0 && (
          <div style={{ color: TEXT_DIM, fontSize: 12, fontStyle: 'italic' }}>
            No events yet — press Play
          </div>
        )}
        {[...eventLog].reverse().map((event, i) => (
          <div key={i} style={{
            fontSize: 11,
            padding: '4px 0',
            borderBottom: `1px solid ${BORDER_COLOR}22`,
            color: event.type === 'narrative' ? ACCENT_COLOR : TEXT_COLOR,
            fontStyle: event.type === 'narrative' ? 'italic' : 'normal',
          }}>
            {event.description}
          </div>
        ))}
      </div>
    </div>
  );
};
