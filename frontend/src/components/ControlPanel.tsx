import React from 'react';
import { SURFACE_COLOR, BORDER_COLOR, ACCENT_COLOR, TEXT_COLOR, TEXT_DIM } from '../utils/colors';

interface Props {
  isPlaying: boolean;
  currentEvent: number;
  totalEvents: number;
  speed: number;
  onPlay: () => void;
  onPause: () => void;
  onStepForward: () => void;
  onStepBackward: () => void;
  onReset: () => void;
  onSpeedChange: (speed: number) => void;
  onCompare?: () => void;
  showCompare?: boolean;
}

const kbdStyle: React.CSSProperties = {
  fontSize: 9,
  color: TEXT_DIM,
  background: '#1a1b26',
  border: `1px solid ${BORDER_COLOR}`,
  borderRadius: 3,
  padding: '1px 4px',
  marginLeft: 4,
  fontFamily: 'inherit',
  verticalAlign: 'middle',
};

const btnStyle: React.CSSProperties = {
  background: 'transparent',
  border: `1px solid ${BORDER_COLOR}`,
  color: TEXT_COLOR,
  padding: '6px 12px',
  borderRadius: 6,
  cursor: 'pointer',
  fontFamily: 'inherit',
  fontSize: 13,
};

export const ControlPanel: React.FC<Props> = ({
  isPlaying, currentEvent, totalEvents, speed,
  onPlay, onPause, onStepForward, onStepBackward, onReset, onSpeedChange,
  onCompare, showCompare,
}) => {
  return (
    <div style={{
      height: 52,
      background: SURFACE_COLOR,
      borderTop: `1px solid ${BORDER_COLOR}`,
      display: 'flex',
      alignItems: 'center',
      padding: '0 16px',
      gap: 8,
    }}>
      <button style={btnStyle} onClick={onReset} title="Reset (R)">|&#9664; Reset<kbd style={kbdStyle}>R</kbd></button>
      <button style={btnStyle} onClick={onStepBackward} title="Step Back (←)">&laquo; Back<kbd style={kbdStyle}>←</kbd></button>
      <button
        style={{ ...btnStyle, background: ACCENT_COLOR + '22', borderColor: ACCENT_COLOR + '44', minWidth: 70 }}
        onClick={isPlaying ? onPause : onPlay}
        title={isPlaying ? 'Pause (Space)' : 'Play (Space)'}
      >
        {isPlaying ? '⏸ Pause' : '▶ Play'}<kbd style={kbdStyle}>Space</kbd>
      </button>
      <button style={btnStyle} onClick={onStepForward} title="Step Forward (→)">Fwd &raquo;<kbd style={kbdStyle}>→</kbd></button>

      <div style={{ marginLeft: 16, display: 'flex', alignItems: 'center', gap: 8, color: TEXT_DIM, fontSize: 12 }}>
        <span>Speed:</span>
        <input
          type="range"
          min={0.1}
          max={2}
          step={0.1}
          value={speed}
          onChange={e => onSpeedChange(parseFloat(e.target.value))}
          style={{ width: 100, accentColor: ACCENT_COLOR }}
        />
        <span style={{ minWidth: 36 }}>{speed}x</span>
      </div>

      {showCompare && onCompare && (

        <button
          style={{ ...btnStyle, marginLeft: 8 }}
          onClick={onCompare}
          title="Compare scenarios"
        >
          Compare
        </button>
      )}


      <div style={{ marginLeft: 'auto', color: TEXT_DIM, fontSize: 12 }}>
        {currentEvent < 0 ? 0 : currentEvent + 1}/{totalEvents}
      </div>
    </div>
  );
};
