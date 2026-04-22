import React, { useEffect, useRef, useState } from 'react';
import { JobState } from '../types';
import { SURFACE_COLOR, BORDER_COLOR, TEXT_COLOR, TEXT_DIM } from '../utils/colors';

export interface ContextMenuAction {
  label: string;
  description: string;
  color: string;
  onClick: () => void;
}

interface Props {
  x: number;
  y: number;
  nodeId: string;
  nodeName: string;
  nodeState: JobState;
  onCancel: (nodeId: string) => void;
  onInjectException: (nodeId: string, message: string) => void;
  onForceComplete: (nodeId: string) => void;
  onClose: () => void;
}

const menuItemStyle: React.CSSProperties = {
  display: 'block',
  width: '100%',
  textAlign: 'left',
  padding: '8px 14px',
  background: 'transparent',
  border: 'none',
  color: TEXT_COLOR,
  fontSize: 12,
  fontFamily: 'inherit',
  cursor: 'pointer',
  borderRadius: 4,
};

export const NodeContextMenu: React.FC<Props> = ({
  x, y, nodeId, nodeName, nodeState,
  onCancel, onInjectException, onForceComplete, onClose,
}) => {
  const menuRef = useRef<HTMLDivElement>(null);
  const [showExceptionPrompt, setShowExceptionPrompt] = useState(false);
  const [exceptionMessage, setExceptionMessage] = useState(`Exception in ${nodeName}`);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);

  const isActive = nodeState === 'Active';
  const isCancellable = nodeState === 'Active' || nodeState === 'Completing';

  // Clamp menu position to viewport
  const menuWidth = 220;
  const menuHeight = showExceptionPrompt ? 200 : 160;
  const clampedX = Math.min(x, window.innerWidth - menuWidth - 10);
  const clampedY = Math.min(y, window.innerHeight - menuHeight - 10);

  return (
    <div
      ref={menuRef}
      style={{
        position: 'fixed',
        left: clampedX,
        top: clampedY,
        width: menuWidth,
        background: SURFACE_COLOR,
        border: `1px solid ${BORDER_COLOR}`,
        borderRadius: 8,
        padding: 4,
        zIndex: 2000,
        boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
      }}
    >
      <div style={{
        padding: '8px 14px 6px',
        fontSize: 11,
        color: TEXT_DIM,
        textTransform: 'uppercase',
        letterSpacing: 1,
        borderBottom: `1px solid ${BORDER_COLOR}`,
        marginBottom: 4,
      }}>
        {nodeName} ({nodeState})
      </div>

      {showExceptionPrompt ? (
        <div style={{ padding: '8px 14px' }}>
          <label style={{ fontSize: 11, color: TEXT_DIM, display: 'block', marginBottom: 4 }}>
            Exception message:
          </label>
          <input
            autoFocus
            style={{
              background: '#1a1b26',
              border: `1px solid ${BORDER_COLOR}`,
              borderRadius: 4,
              color: TEXT_COLOR,
              padding: '4px 8px',
              fontSize: 12,
              fontFamily: 'inherit',
              width: '100%',
              boxSizing: 'border-box',
              marginBottom: 8,
            }}
            value={exceptionMessage}
            onChange={e => setExceptionMessage(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                onInjectException(nodeId, exceptionMessage);
                onClose();
              }
            }}
          />
          <div style={{ display: 'flex', gap: 6 }}>
            <button
              style={{ ...menuItemStyle, color: '#f7768e', padding: '4px 8px', fontSize: 11 }}
              onClick={() => {
                onInjectException(nodeId, exceptionMessage);
                onClose();
              }}
            >
              Inject
            </button>
            <button
              style={{ ...menuItemStyle, color: TEXT_DIM, padding: '4px 8px', fontSize: 11 }}
              onClick={() => setShowExceptionPrompt(false)}
            >
              Back
            </button>
          </div>
        </div>
      ) : (
        <>
          <button
            style={{
              ...menuItemStyle,
              color: isCancellable ? '#ff9e64' : TEXT_DIM,
              cursor: isCancellable ? 'pointer' : 'default',
              opacity: isCancellable ? 1 : 0.4,
            }}
            disabled={!isCancellable}
            onClick={() => { onCancel(nodeId); onClose(); }}
            onMouseEnter={e => { if (isCancellable) (e.target as HTMLElement).style.background = '#ffffff0a'; }}
            onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
          >
            Cancel Node
            <div style={{ fontSize: 10, color: TEXT_DIM, marginTop: 2 }}>Cancel this node and descendants</div>
          </button>

          <button
            style={{
              ...menuItemStyle,
              color: isActive ? '#f7768e' : TEXT_DIM,
              cursor: isActive ? 'pointer' : 'default',
              opacity: isActive ? 1 : 0.4,
            }}
            disabled={!isActive}
            onClick={() => setShowExceptionPrompt(true)}
            onMouseEnter={e => { if (isActive) (e.target as HTMLElement).style.background = '#ffffff0a'; }}
            onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
          >
            Inject Exception
            <div style={{ fontSize: 10, color: TEXT_DIM, marginTop: 2 }}>Propagate exception upward</div>
          </button>

          <button
            style={{
              ...menuItemStyle,
              color: isActive ? '#9ece6a' : TEXT_DIM,
              cursor: isActive ? 'pointer' : 'default',
              opacity: isActive ? 1 : 0.4,
            }}
            disabled={!isActive}
            onClick={() => { onForceComplete(nodeId); onClose(); }}
            onMouseEnter={e => { if (isActive) (e.target as HTMLElement).style.background = '#ffffff0a'; }}
            onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
          >
            Force Complete
            <div style={{ fontSize: 10, color: TEXT_DIM, marginTop: 2 }}>Complete this node immediately</div>
          </button>
        </>
      )}
    </div>
  );
};
