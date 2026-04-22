import React from 'react';
import { JobState } from '../types';
import { STATE_COLORS, TEXT_DIM } from '../utils/colors';

const states: JobState[] = ['New', 'Active', 'Suspended', 'Completing', 'Completed', 'Cancelling', 'Cancelled'];

export const LegendPanel: React.FC = () => {
  return (
    <div style={{ marginBottom: 24 }}>
      <h3 style={{ fontSize: 11, color: TEXT_DIM, letterSpacing: 2, marginBottom: 12 }}>STATE LEGEND</h3>
      {states.map(s => (
        <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, fontSize: 12 }}>
          <span style={{
            display: 'inline-block',
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: STATE_COLORS[s],
          }} />
          <span>{s}</span>
        </div>
      ))}
    </div>
  );
};
