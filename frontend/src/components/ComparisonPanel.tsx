import React, { useState, useMemo } from 'react';
import { useBuilderState } from '../hooks/useBuilderState';
import { BuilderNodeForm } from '../builder/BuilderNodeForm';
import { BuilderTreePreview } from '../builder/BuilderTreePreview';
import { BuilderNodeConfig } from '../builder/types';
import { coroutineNodeToBuilderWithEvents, generateComparisonTimeline } from '../builder/comparisonTimelineGenerator';
import { EventTimeline } from '../types';
import { BORDER_COLOR, BG_COLOR, TEXT_COLOR, TEXT_DIM, ACCENT_COLOR } from '../utils/colors';

interface Props {
  originalTimeline: EventTimeline;
  onClose: () => void;
  onGenerate: (timeline: EventTimeline) => void;
}

function flattenBuilderTree(root: BuilderNodeConfig): { node: BuilderNodeConfig; isRoot: boolean }[] {
  const result: { node: BuilderNodeConfig; isRoot: boolean }[] = [];
  const visit = (node: BuilderNodeConfig, isRoot: boolean) => {
    result.push({ node, isRoot });
    node.children.forEach(c => visit(c, false));
  };
  visit(root, true);
  return result;
}

/** Deep-clone a builder tree */
function cloneTree(node: BuilderNodeConfig): BuilderNodeConfig {
  return {
    ...node,
    failure: node.failure ? { ...node.failure } : undefined,
    children: node.children.map(cloneTree),
  };
}

const overlayStyle: React.CSSProperties = {
  position: 'fixed',
  inset: 0,
  background: 'rgba(0,0,0,0.6)',
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
  zIndex: 1000,
};

const panelStyle: React.CSSProperties = {
  background: BG_COLOR,
  border: `1px solid ${BORDER_COLOR}`,
  borderRadius: 12,
  width: 1000,
  maxHeight: '90vh',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
};

const btnStyle: React.CSSProperties = {
  background: ACCENT_COLOR,
  border: 'none',
  borderRadius: 6,
  color: '#1a1b26',
  padding: '8px 20px',
  fontSize: 13,
  fontWeight: 600,
  fontFamily: 'inherit',
  cursor: 'pointer',
};

const quickBtnStyle: React.CSSProperties = {
  background: 'transparent',
  border: `1px solid ${BORDER_COLOR}`,
  borderRadius: 4,
  color: TEXT_COLOR,
  padding: '4px 10px',
  fontSize: 11,
  fontFamily: 'inherit',
  cursor: 'pointer',
};

export const ComparisonPanel: React.FC<Props> = ({ originalTimeline, onClose, onGenerate }) => {
  const baseBuilderTree = useMemo(
    () => coroutineNodeToBuilderWithEvents(originalTimeline.tree, originalTimeline.events),
    [originalTimeline]
  );
  const [rightState, rightActions] = useBuilderState();
  const [initialized, setInitialized] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize right tree as a copy of the base tree
  React.useEffect(() => {
    if (!initialized) {
      rightActions.loadTree(cloneTree(baseBuilderTree));
      setInitialized(true);
    }
  }, [initialized, baseBuilderTree, rightActions]);

  const rightNodes = flattenBuilderTree(rightState.root);

  const handleQuickAction = (action: 'supervisorJob' | 'addFailure' | 'removeFailure') => {
    const selectedId = rightState.selectedNodeId;
    if (!selectedId) {
      setError('Select a node first');
      return;
    }
    setError(null);

    switch (action) {
      case 'supervisorJob':
        rightActions.updateNode(selectedId, { jobType: 'SupervisorJob' });
        break;
      case 'addFailure':
        rightActions.updateNode(selectedId, {
          failure: { exceptionMessage: 'Simulated failure', timingMs: 1000 },
        });
        break;
      case 'removeFailure':
        rightActions.updateNode(selectedId, { failure: undefined });
        break;
    }
  };

  const handleCompare = () => {
    setError(null);
    try {
      const timeline = generateComparisonTimeline(
        originalTimeline,
        rightState.root,
        `${originalTimeline.scenarioName} (modified)`,
      );
      onGenerate(timeline);
    } catch (e) {
      setError(`Generation failed: ${e}`);
    }
  };

  return (
    <div style={overlayStyle} onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div style={panelStyle}>
        {/* Header */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '16px 20px',
          borderBottom: `1px solid ${BORDER_COLOR}`,
        }}>
          <h2 style={{ margin: 0, fontSize: 16, color: TEXT_COLOR }}>Compare Scenarios</h2>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: TEXT_DIM,
              fontSize: 20,
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            x
          </button>
        </div>

        {/* Body */}
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
          {/* Left: Base tree (read-only) */}
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            padding: 16,
            borderRight: `1px solid ${BORDER_COLOR}`,
          }}>
            <div style={{
              fontSize: 11,
              color: TEXT_DIM,
              marginBottom: 8,
              textTransform: 'uppercase',
              letterSpacing: 1,
            }}>
              Base (read-only)
            </div>
            <BuilderTreePreview
              root={baseBuilderTree}
              selectedNodeId={null}
              onNodeClick={() => {}}
              width={420}
              height={300}
            />
          </div>

          {/* Right: Editable tree */}
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            padding: 16,
            overflow: 'hidden',
          }}>
            <div style={{
              fontSize: 11,
              color: TEXT_DIM,
              marginBottom: 8,
              textTransform: 'uppercase',
              letterSpacing: 1,
            }}>
              Modified (editable)
            </div>

            {/* Quick actions */}
            <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
              <button style={quickBtnStyle} onClick={() => handleQuickAction('supervisorJob')}>
                SupervisorJob
              </button>
              <button style={quickBtnStyle} onClick={() => handleQuickAction('addFailure')}>
                + Failure
              </button>
              <button style={quickBtnStyle} onClick={() => handleQuickAction('removeFailure')}>
                - Failure
              </button>
            </div>

            <BuilderTreePreview
              root={rightState.root}
              selectedNodeId={rightState.selectedNodeId}
              onNodeClick={rightActions.selectNode}
              width={420}
              height={200}
            />

            {/* Node forms */}
            <div style={{ flex: 1, overflowY: 'auto', marginTop: 8 }}>
              {rightNodes.map(({ node, isRoot }) => (
                <BuilderNodeForm
                  key={node.id}
                  node={node}
                  isRoot={isRoot}
                  onUpdate={rightActions.updateNode}
                  onAddChild={rightActions.addChild}
                  onRemove={rightActions.removeNode}
                />
              ))}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div style={{
          padding: '12px 20px',
          borderTop: `1px solid ${BORDER_COLOR}`,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            {error && <span style={{ color: '#f7768e', fontSize: 12 }}>{error}</span>}
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button
              onClick={onClose}
              style={{
                ...btnStyle,
                background: 'transparent',
                border: `1px solid ${BORDER_COLOR}`,
                color: TEXT_COLOR,
              }}
            >
              Cancel
            </button>
            <button onClick={handleCompare} style={btnStyle}>
              Compare & Play
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
