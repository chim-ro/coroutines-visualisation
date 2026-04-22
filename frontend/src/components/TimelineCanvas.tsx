import React from 'react';
import { LayoutNode, JobState, ThreadLane, EventTimeline, SimulationEvent } from '../types';
import { STATE_COLORS, TEXT_COLOR, TEXT_DIM, BORDER_COLOR, SURFACE_COLOR } from '../utils/colors';

interface TimelineCanvasProps {
  layoutRoot: LayoutNode | null;
  secondLayoutRoot: LayoutNode | null;
  nodeStates: Map<string, JobState>;
  eventLog: SimulationEvent[];
  timeline: EventTimeline | null;
  currentTimeMs: number;
}

const SEGMENT_COLORS: Record<string, string> = {
  active: '#9ece6a',    // green
  blocked: '#f7768e',   // red/orange
  suspended: '#bb9af7', // purple
};

const LANE_HEIGHT = 32;
const LANE_GAP = 6;
const LABEL_WIDTH = 100;

const TimelineCanvas: React.FC<TimelineCanvasProps> = ({
  layoutRoot,
  secondLayoutRoot,
  nodeStates,
  eventLog,
  timeline,
  currentTimeMs,
}) => {
  const leftLanes = timeline?.leftThreadLanes ?? null;
  const rightLanes = timeline?.rightThreadLanes ?? null;
  const totalDuration = timeline?.totalDurationMs ?? 1;

  // Find latest narrative event
  const lastNarrative = [...eventLog].reverse().find(e => e.type === 'narrative');

  // Playhead position as percentage
  const playheadPct = totalDuration > 0 ? (currentTimeMs / totalDuration) * 100 : 0;

  const renderGanttColumn = (
    lanes: ThreadLane[] | null,
    title: string,
    threadCountLabel: string,
  ) => {
    if (!lanes) return null;
    const columnHeight = lanes.length * (LANE_HEIGHT + LANE_GAP) + LANE_GAP;

    return (
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '16px 20px', minWidth: 0 }}>
        {/* Column header */}
        <div style={{
          fontSize: 14,
          fontWeight: 700,
          color: TEXT_COLOR,
          marginBottom: 12,
          letterSpacing: 0.5,
        }}>
          {title}
        </div>

        {/* Gantt chart area */}
        <div style={{ position: 'relative', minHeight: columnHeight }}>
          {/* Lanes */}
          {lanes.map((lane, laneIdx) => (
            <div
              key={lane.threadName}
              style={{
                display: 'flex',
                alignItems: 'center',
                height: LANE_HEIGHT,
                marginBottom: LANE_GAP,
              }}
            >
              {/* Thread label */}
              <div style={{
                width: LABEL_WIDTH,
                fontSize: 11,
                fontWeight: 500,
                color: TEXT_DIM,
                textAlign: 'right',
                paddingRight: 10,
                flexShrink: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}>
                {lane.threadName}
              </div>

              {/* Bar area */}
              <div style={{
                flex: 1,
                position: 'relative',
                height: '100%',
                background: `${BORDER_COLOR}40`,
                borderRadius: 4,
                overflow: 'hidden',
              }}>
                {/* Segments */}
                {lane.segments.map((seg, segIdx) => {
                  const leftPct = (seg.startMs / totalDuration) * 100;
                  const widthPct = ((seg.endMs - seg.startMs) / totalDuration) * 100;
                  const isPast = seg.startMs <= currentTimeMs;
                  const isPartial = seg.startMs <= currentTimeMs && seg.endMs > currentTimeMs;
                  const color = SEGMENT_COLORS[seg.state] ?? '#565f89';

                  return (
                    <div
                      key={`${seg.taskId}-${segIdx}`}
                      style={{
                        position: 'absolute',
                        left: `${leftPct}%`,
                        width: `${widthPct}%`,
                        top: 2,
                        bottom: 2,
                        borderRadius: 3,
                        background: color,
                        opacity: isPast ? 1 : 0.12,
                        transition: 'opacity 0.3s ease',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        overflow: 'hidden',
                        boxShadow: isPast && seg.state === 'active' ? `0 0 8px ${color}55` : 'none',
                      }}
                      title={`${seg.taskName} (${seg.state}) ${seg.startMs}ms–${seg.endMs}ms`}
                    >
                      {widthPct > 6 && (
                        <span style={{
                          fontSize: 9,
                          fontWeight: 600,
                          color: isPast ? '#1a1b26' : TEXT_DIM,
                          letterSpacing: 0.3,
                          textTransform: 'uppercase',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          padding: '0 4px',
                        }}>
                          {seg.taskName}
                        </span>
                      )}
                    </div>
                  );
                })}

                {/* Playhead */}
                {playheadPct > 0 && (
                  <div style={{
                    position: 'absolute',
                    left: `${Math.min(playheadPct, 100)}%`,
                    top: 0,
                    bottom: 0,
                    width: 2,
                    background: '#7aa2f7',
                    zIndex: 2,
                    transition: 'left 0.15s ease-out',
                    boxShadow: '0 0 6px #7aa2f7aa',
                  }} />
                )}
              </div>
            </div>
          ))}

          {/* Time axis label */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            marginTop: 4,
            paddingLeft: LABEL_WIDTH,
          }}>
            <div style={{
              flex: 1,
              height: 1,
              background: `${TEXT_DIM}40`,
              position: 'relative',
            }}>
              <span style={{
                position: 'absolute',
                right: 0,
                top: -8,
                fontSize: 9,
                color: TEXT_DIM,
              }}>
                {totalDuration}ms →
              </span>
              <span style={{
                position: 'absolute',
                left: 0,
                top: -8,
                fontSize: 9,
                color: TEXT_DIM,
              }}>
                0ms
              </span>
            </div>
          </div>
        </div>

        {/* Thread count badge */}
        <div style={{
          marginTop: 12,
          fontSize: 12,
          fontWeight: 600,
          color: lanes.length <= 2 ? '#9ece6a' : '#f7768e',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}>
          <span style={{
            display: 'inline-block',
            width: 20,
            height: 20,
            borderRadius: '50%',
            background: lanes.length <= 2 ? '#9ece6a22' : '#f7768e22',
            textAlign: 'center',
            lineHeight: '20px',
            fontSize: 11,
            fontWeight: 700,
          }}>
            {lanes.length}
          </span>
          {threadCountLabel}
        </div>
      </div>
    );
  };

  // Fallback to old bar-chart rendering if no thread lanes
  if (!leftLanes && !rightLanes) {
    return <OldTimelineCanvas
      layoutRoot={layoutRoot}
      secondLayoutRoot={secondLayoutRoot}
      nodeStates={nodeStates}
      eventLog={eventLog}
    />;
  }

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Gantt area */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'flex-start',
        minHeight: 0,
        overflowY: 'auto',
        paddingTop: 8,
      }}>
        {renderGanttColumn(
          leftLanes,
          'Threads',
          `thread${leftLanes && leftLanes.length !== 1 ? 's' : ''} needed`,
        )}

        {/* Vertical divider */}
        {leftLanes && rightLanes && (
          <div style={{
            width: 1,
            alignSelf: 'stretch',
            background: BORDER_COLOR,
            margin: '16px 0',
          }} />
        )}

        {renderGanttColumn(
          rightLanes,
          'Coroutines',
          `thread${rightLanes && rightLanes.length !== 1 ? 's' : ''} needed`,
        )}
      </div>

      {/* Legend */}
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        gap: 20,
        padding: '6px 16px',
        borderTop: `1px solid ${BORDER_COLOR}`,
        background: SURFACE_COLOR,
        flexShrink: 0,
      }}>
        {Object.entries(SEGMENT_COLORS).map(([state, color]) => (
          <div key={state} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10, color: TEXT_DIM }}>
            <div style={{ width: 12, height: 12, borderRadius: 2, background: color }} />
            <span style={{ textTransform: 'capitalize' }}>{state}</span>
          </div>
        ))}
      </div>

      {/* Narrative bar at bottom */}
      {lastNarrative && (
        <div style={{
          padding: '10px 20px',
          background: SURFACE_COLOR,
          borderTop: `1px solid ${BORDER_COLOR}`,
          fontSize: 12,
          color: TEXT_COLOR,
          textAlign: 'center',
          flexShrink: 0,
        }}>
          {lastNarrative.description}
        </div>
      )}
    </div>
  );
};

// Fallback for timelines without thread lane data (e.g., custom scenarios)
const OldTimelineCanvas: React.FC<{
  layoutRoot: LayoutNode | null;
  secondLayoutRoot: LayoutNode | null;
  nodeStates: Map<string, JobState>;
  eventLog: SimulationEvent[];
}> = ({ layoutRoot, secondLayoutRoot, nodeStates, eventLog }) => {
  const leftTasks = layoutRoot ? layoutRoot.children : [];
  const rightTasks = secondLayoutRoot ? secondLayoutRoot.children : [];
  const lastNarrative = [...eventLog].reverse().find(e => e.type === 'narrative');

  const barHeight = 44;
  const barGap = 10;

  const renderColumn = (root: LayoutNode | null, tasks: LayoutNode[]) => {
    if (!root) return null;
    const rootState = nodeStates.get(root.id) ?? root.state;
    return (
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '16px 20px' }}>
        <div style={{
          fontSize: 13, fontWeight: 600, color: TEXT_COLOR, marginBottom: 16,
          display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <span>{root.displayName}</span>
          <span style={{
            fontSize: 10, color: STATE_COLORS[rootState],
            background: `${STATE_COLORS[rootState]}18`, padding: '2px 8px', borderRadius: 3,
          }}>
            {rootState}
          </span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: barGap }}>
          {tasks.map(task => {
            const state = nodeStates.get(task.id) ?? task.state;
            const color = STATE_COLORS[state];
            return (
              <div key={task.id} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{
                  width: 90, fontSize: 12, color: TEXT_DIM, textAlign: 'right', flexShrink: 0,
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                }}>
                  {task.displayName}
                </div>
                <div style={{
                  flex: 1, height: barHeight, borderRadius: 6, background: color,
                  opacity: state === 'New' ? 0.25 : 1,
                  transition: 'background 0.4s ease, opacity 0.4s ease',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: state === 'Active' ? `0 0 12px ${color}66` : 'none',
                }}>
                  <span style={{
                    fontSize: 11, fontWeight: 600,
                    color: state === 'New' ? TEXT_DIM : '#1a1b26',
                    letterSpacing: 0.5, textTransform: 'uppercase',
                  }}>
                    {state}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', minHeight: 0 }}>
        <div style={{ flex: 1, display: 'flex', maxWidth: '100%' }}>
          {renderColumn(layoutRoot, leftTasks)}
          {secondLayoutRoot && (
            <div style={{ width: 1, alignSelf: 'stretch', background: BORDER_COLOR, margin: '16px 0' }} />
          )}
          {renderColumn(secondLayoutRoot, rightTasks)}
        </div>
      </div>
      {lastNarrative && (
        <div style={{
          padding: '10px 20px', background: SURFACE_COLOR,
          borderTop: `1px solid ${BORDER_COLOR}`,
          fontSize: 12, color: TEXT_COLOR, textAlign: 'center', flexShrink: 0,
        }}>
          {lastNarrative.description}
        </div>
      )}
    </div>
  );
};

export { TimelineCanvas };
