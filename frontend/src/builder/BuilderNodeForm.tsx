import React from 'react';
import { BuilderNodeConfig } from './types';
import { BuilderType, JobType } from '../types';
import { SURFACE_COLOR, BORDER_COLOR, TEXT_COLOR, TEXT_DIM, ACCENT_COLOR } from '../utils/colors';

interface Props {
  node: BuilderNodeConfig;
  isRoot: boolean;
  onUpdate: (nodeId: string, updates: Partial<BuilderNodeConfig>) => void;
  onAddChild: (parentId: string) => void;
  onRemove: (nodeId: string) => void;
}

const BUILDER_OPTIONS: BuilderType[] = ['Launch', 'Async', 'CoroutineScope', 'SupervisorScope', 'RunBlocking'];
const JOB_TYPE_OPTIONS: JobType[] = ['Job', 'SupervisorJob'];

const inputStyle: React.CSSProperties = {
  background: '#1a1b26',
  border: `1px solid ${BORDER_COLOR}`,
  borderRadius: 4,
  color: TEXT_COLOR,
  padding: '4px 8px',
  fontSize: 12,
  fontFamily: 'inherit',
  width: '100%',
  boxSizing: 'border-box',
};

const selectStyle: React.CSSProperties = {
  ...inputStyle,
  cursor: 'pointer',
};

const smallBtnStyle: React.CSSProperties = {
  background: 'transparent',
  border: `1px solid ${BORDER_COLOR}`,
  borderRadius: 4,
  color: TEXT_COLOR,
  padding: '4px 10px',
  fontSize: 11,
  fontFamily: 'inherit',
  cursor: 'pointer',
  marginRight: 6,
};

export const BuilderNodeForm: React.FC<Props> = ({ node, isRoot, onUpdate, onAddChild, onRemove }) => {
  const hasFailure = !!node.failure;

  return (
    <div style={{
      background: SURFACE_COLOR,
      border: `1px solid ${BORDER_COLOR}`,
      borderRadius: 8,
      padding: 12,
      marginBottom: 8,
    }}>
      <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 8, textTransform: 'uppercase', letterSpacing: 1 }}>
        {isRoot ? 'Root Node' : `Node: ${node.id}`}
      </div>

      <div style={{ marginBottom: 6 }}>
        <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 2 }}>Name</label>
        <input
          style={inputStyle}
          value={node.displayName}
          onChange={e => onUpdate(node.id, { displayName: e.target.value })}
          placeholder="Node name"
        />
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 2 }}>Builder</label>
          <select
            style={selectStyle}
            value={node.builder}
            onChange={e => onUpdate(node.id, { builder: e.target.value as BuilderType })}
          >
            {BUILDER_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
          </select>
        </div>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 2 }}>Job Type</label>
          <select
            style={selectStyle}
            value={node.jobType}
            onChange={e => onUpdate(node.id, { jobType: e.target.value as JobType })}
          >
            {JOB_TYPE_OPTIONS.map(j => <option key={j} value={j}>{j}</option>)}
          </select>
        </div>
      </div>

      {/* Failure toggle */}
      <div style={{ marginBottom: 8 }}>
        <label style={{ fontSize: 11, color: TEXT_DIM, display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={hasFailure}
            onChange={e => {
              if (e.target.checked) {
                onUpdate(node.id, {
                  failure: { exceptionMessage: 'Exception in ' + node.displayName, timingMs: 1000 },
                });
              } else {
                onUpdate(node.id, { failure: undefined });
              }
            }}
          />
          Throws exception
        </label>
        {hasFailure && (
          <div style={{ marginTop: 6, paddingLeft: 4 }}>
            <div style={{ marginBottom: 4 }}>
              <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 2 }}>Message</label>
              <input
                style={inputStyle}
                value={node.failure!.exceptionMessage}
                onChange={e => onUpdate(node.id, {
                  failure: { ...node.failure!, exceptionMessage: e.target.value },
                })}
              />
            </div>
            <div>
              <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 2 }}>Timing (ms)</label>
              <input
                style={{ ...inputStyle, width: 100 }}
                type="number"
                min={100}
                step={100}
                value={node.failure!.timingMs}
                onChange={e => onUpdate(node.id, {
                  failure: { ...node.failure!, timingMs: parseInt(e.target.value) || 1000 },
                })}
              />
            </div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', flexWrap: 'wrap' }}>
        <button
          style={{ ...smallBtnStyle, color: ACCENT_COLOR, borderColor: ACCENT_COLOR + '44' }}
          onClick={() => onAddChild(node.id)}
        >
          + Add Child
        </button>
        {!isRoot && (
          <button
            style={{ ...smallBtnStyle, color: '#f7768e', borderColor: '#f7768e44' }}
            onClick={() => onRemove(node.id)}
          >
            Remove
          </button>
        )}
      </div>
    </div>
  );
};
