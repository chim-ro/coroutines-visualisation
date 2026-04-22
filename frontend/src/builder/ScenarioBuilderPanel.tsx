import React, { useState } from 'react';
import { useBuilderState } from '../hooks/useBuilderState';
import { BuilderNodeForm } from './BuilderNodeForm';
import { BuilderTreePreview } from './BuilderTreePreview';
import { BuilderNodeConfig, CustomScenario } from './types';
import { generateTimeline } from './timelineGenerator';
import { EventTimeline } from '../types';
import { SURFACE_COLOR, BORDER_COLOR, BG_COLOR, TEXT_COLOR, TEXT_DIM, ACCENT_COLOR } from '../utils/colors';

interface Props {
  onClose: () => void;
  onGenerate: (scenario: CustomScenario, timeline: EventTimeline) => void;
  editingScenario?: CustomScenario;
}

// Flatten the tree into a list for the form
function flattenBuilderTree(root: BuilderNodeConfig): { node: BuilderNodeConfig; isRoot: boolean }[] {
  const result: { node: BuilderNodeConfig; isRoot: boolean }[] = [];
  const visit = (node: BuilderNodeConfig, isRoot: boolean) => {
    result.push({ node, isRoot });
    node.children.forEach(c => visit(c, false));
  };
  visit(root, true);
  return result;
}

function validate(root: BuilderNodeConfig): string | null {
  const nodes = flattenBuilderTree(root);
  if (nodes.length === 0) return 'Tree must have at least one node.';
  for (const { node } of nodes) {
    if (!node.displayName.trim()) return `Node ${node.id} has an empty name.`;
    if (node.failure && node.failure.timingMs < 100) return `Failure timing for ${node.displayName} must be >= 100ms.`;
  }
  return null;
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
  width: 860,
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

export const ScenarioBuilderPanel: React.FC<Props> = ({ onClose, onGenerate, editingScenario }) => {
  const [builderState, builderActions] = useBuilderState();
  const [scenarioName, setScenarioName] = useState(editingScenario?.name ?? 'My Scenario');
  const [error, setError] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);

  // Load existing tree when editing
  React.useEffect(() => {
    if (editingScenario && !initialized) {
      builderActions.loadTree(editingScenario.tree);
      setInitialized(true);
    }
  }, [editingScenario, initialized, builderActions]);

  const handleGenerate = () => {
    const validationError = validate(builderState.root);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (!scenarioName.trim()) {
      setError('Scenario name is required.');
      return;
    }
    setError(null);

    const scenario: CustomScenario = {
      id: editingScenario?.id ?? `custom-${Date.now()}`,
      name: scenarioName.trim(),
      tree: builderState.root,
      createdAt: editingScenario?.createdAt ?? Date.now(),
    };

    const timeline = generateTimeline(builderState.root, scenarioName.trim());
    onGenerate(scenario, timeline);
  };

  const allNodes = flattenBuilderTree(builderState.root);

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
          <h2 style={{ margin: 0, fontSize: 16, color: TEXT_COLOR }}>Custom Scenario Builder</h2>
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
          {/* Left: Node forms */}
          <div style={{
            width: 400,
            overflowY: 'auto',
            padding: 16,
            borderRight: `1px solid ${BORDER_COLOR}`,
          }}>
            <div style={{ marginBottom: 12 }}>
              <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 4, textTransform: 'uppercase', letterSpacing: 1 }}>
                Scenario Name
              </label>
              <input
                style={{
                  background: '#1a1b26',
                  border: `1px solid ${BORDER_COLOR}`,
                  borderRadius: 4,
                  color: TEXT_COLOR,
                  padding: '6px 10px',
                  fontSize: 13,
                  fontFamily: 'inherit',
                  width: '100%',
                  boxSizing: 'border-box',
                }}
                value={scenarioName}
                onChange={e => setScenarioName(e.target.value)}
              />
            </div>

            <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 8, textTransform: 'uppercase', letterSpacing: 1 }}>
              Nodes ({allNodes.length})
            </div>

            {allNodes.map(({ node, isRoot }) => (
              <BuilderNodeForm
                key={node.id}
                node={node}
                isRoot={isRoot}
                onUpdate={builderActions.updateNode}
                onAddChild={builderActions.addChild}
                onRemove={builderActions.removeNode}
              />
            ))}
          </div>

          {/* Right: Tree preview */}
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16,
          }}>
            <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 8, textTransform: 'uppercase', letterSpacing: 1 }}>
              Tree Preview
            </div>
            <BuilderTreePreview
              root={builderState.root}
              selectedNodeId={builderState.selectedNodeId}
              onNodeClick={builderActions.selectNode}
              width={420}
              height={350}
            />
            <div style={{ fontSize: 11, color: TEXT_DIM, marginTop: 8 }}>
              Click a node to select it
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
              onClick={() => { builderActions.reset(); setError(null); }}
              style={{
                ...btnStyle,
                background: 'transparent',
                border: `1px solid ${BORDER_COLOR}`,
                color: TEXT_COLOR,
              }}
            >
              Reset
            </button>
            <button onClick={handleGenerate} style={btnStyle}>
              {editingScenario ? 'Save & Play' : 'Generate & Play'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
