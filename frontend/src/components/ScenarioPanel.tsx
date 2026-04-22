import React, { useEffect, useState } from 'react';
import { ScenarioInfo } from '../types';
import { fetchScenarios } from '../api/scenarioApi';
import { SURFACE_COLOR, BORDER_COLOR, ACCENT_COLOR, TEXT_COLOR, TEXT_DIM } from '../utils/colors';

interface CustomScenarioEntry {
  id: string;
  name: string;
}

interface Props {
  activeScenarioId: string | null;
  onSelectScenario: (id: string) => void;
  onCreateScenario?: () => void;
  customScenarios?: CustomScenarioEntry[];
  onSelectCustomScenario?: (id: string) => void;
  onDeleteCustomScenario?: (id: string) => void;
  onEditCustomScenario?: (id: string) => void;
}

export const ScenarioPanel: React.FC<Props> = ({
  activeScenarioId, onSelectScenario,
  onCreateScenario, customScenarios = [], onSelectCustomScenario, onDeleteCustomScenario, onEditCustomScenario,
}) => {
  const [scenarios, setScenarios] = useState<ScenarioInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchScenarios()
      .then(setScenarios)
      .catch(e => setError(e.message));
  }, []);

  const grouped = scenarios.reduce<Record<string, ScenarioInfo[]>>((acc, s) => {
    (acc[s.category] ??= []).push(s);
    return acc;
  }, {});

  return (
    <div style={{
      width: 220,
      minWidth: 220,
      background: SURFACE_COLOR,
      borderRight: `1px solid ${BORDER_COLOR}`,
      padding: 16,
      overflowY: 'auto',
    }}>
      <h2 style={{ fontSize: 13, color: TEXT_DIM, letterSpacing: 2, marginBottom: 16 }}>
        SCENARIOS
      </h2>
      {error && <div style={{ color: '#f7768e', fontSize: 12 }}>{error}</div>}
      {Object.entries(grouped).map(([category, items]) => (
        <div key={category} style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 6, textTransform: 'uppercase' }}>
            {category}
          </div>
          {items.map(s => (
            <button
              key={s.id}
              onClick={() => onSelectScenario(s.id)}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                padding: '8px 10px',
                marginBottom: 2,
                background: activeScenarioId === s.id ? ACCENT_COLOR + '22' : 'transparent',
                border: activeScenarioId === s.id ? `1px solid ${ACCENT_COLOR}44` : '1px solid transparent',
                borderRadius: 6,
                color: activeScenarioId === s.id ? ACCENT_COLOR : TEXT_COLOR,
                cursor: 'pointer',
                fontSize: 13,
                fontFamily: 'inherit',
              }}
              title={s.description}
            >
              {activeScenarioId === s.id ? '● ' : '  '}{s.name}
            </button>
          ))}
        </div>
      ))}

      {/* Custom Scenarios */}
      {customScenarios.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: TEXT_DIM, marginBottom: 6, textTransform: 'uppercase' }}>
            Custom
          </div>
          {customScenarios.map(s => (
            <div key={s.id} style={{ display: 'flex', alignItems: 'center', marginBottom: 2 }}>
              <button
                onClick={() => onSelectCustomScenario?.(s.id)}
                style={{
                  flex: 1,
                  display: 'block',
                  textAlign: 'left',
                  padding: '8px 10px',
                  background: activeScenarioId === s.id ? ACCENT_COLOR + '22' : 'transparent',
                  border: activeScenarioId === s.id ? `1px solid ${ACCENT_COLOR}44` : '1px solid transparent',
                  borderRadius: 6,
                  color: activeScenarioId === s.id ? ACCENT_COLOR : TEXT_COLOR,
                  cursor: 'pointer',
                  fontSize: 13,
                  fontFamily: 'inherit',
                }}
              >
                {activeScenarioId === s.id ? '● ' : '  '}{s.name}
              </button>
              <button
                onClick={() => onEditCustomScenario?.(s.id)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: TEXT_DIM,
                  cursor: 'pointer',
                  fontSize: 12,
                  padding: '2px 5px',
                  fontFamily: 'inherit',
                }}
                title="Edit custom scenario"
              >
                &#9998;
              </button>
              <button
                onClick={() => onDeleteCustomScenario?.(s.id)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: TEXT_DIM,
                  cursor: 'pointer',
                  fontSize: 14,
                  padding: '2px 6px',
                  fontFamily: 'inherit',
                }}
                title="Delete custom scenario"
              >
                x
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Create button */}
      {onCreateScenario && (
        <button
          onClick={onCreateScenario}
          style={{
            display: 'block',
            width: '100%',
            textAlign: 'center',
            padding: '10px',
            background: 'transparent',
            border: `1px dashed ${BORDER_COLOR}`,
            borderRadius: 6,
            color: ACCENT_COLOR,
            cursor: 'pointer',
            fontSize: 12,
            fontFamily: 'inherit',
            marginTop: 8,
          }}
        >
          + Create Scenario
        </button>
      )}
    </div>
  );
};
