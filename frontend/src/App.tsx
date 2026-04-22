import React, { useState, useCallback, useEffect } from 'react';
import { ScenarioPanel } from './components/ScenarioPanel';
import { TreeCanvas } from './components/TreeCanvas';
import { TimelineCanvas } from './components/TimelineCanvas';
import { ControlPanel } from './components/ControlPanel';
import { LegendPanel } from './components/LegendPanel';
import { InfoPanel } from './components/InfoPanel';
import { NodeContextMenu } from './components/NodeContextMenu';
import { CodePanel } from './components/CodePanel';
import { ScenarioBuilderPanel } from './builder/ScenarioBuilderPanel';
import { useAnimationEngine } from './hooks/useAnimationEngine';
import { fetchTimeline } from './api/scenarioApi';
import { CustomScenario } from './builder/types';
import { EventTimeline, LayoutNode } from './types';
import { generateCancelEvents, generateExceptionEvents, generateForceCompleteEvents } from './manipulation/eventInjector';
import { flattenTree } from './rendering/treeLayout';
import { SURFACE_COLOR, BORDER_COLOR, BG_COLOR } from './utils/colors';

const STORAGE_KEY = 'coroutines-viz-custom-scenarios';

interface StoredCustomScenario {
  scenario: CustomScenario;
  timeline: EventTimeline;
}

function loadCustomScenarios(): StoredCustomScenario[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveCustomScenarios(scenarios: StoredCustomScenario[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(scenarios));
}

function findNode(root: LayoutNode | null, nodeId: string): LayoutNode | null {
  if (!root) return null;
  const nodes = flattenTree(root);
  return nodes.find(n => n.id === nodeId) ?? null;
}

const App: React.FC = () => {
  const [activeScenarioId, setActiveScenarioId] = useState<string | null>(null);
  const [activeLevel, setActiveLevel] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [animState, controls] = useAnimationEngine();
  const [showBuilder, setShowBuilder] = useState(false);
  const [customScenarios, setCustomScenarios] = useState<StoredCustomScenario[]>(loadCustomScenarios);
  const [editingScenarioId, setEditingScenarioId] = useState<string | null>(null);
  const [contextMenu, setContextMenu] = useState<{ nodeId: string; x: number; y: number } | null>(null);
  const [kotlinCode, setKotlinCode] = useState('');

  // Persist custom scenarios
  useEffect(() => {
    saveCustomScenarios(customScenarios);
  }, [customScenarios]);

  const loadScenario = useCallback(async (id: string, level: string) => {
    setSelectedNodeId(null);
    setContextMenu(null);
    try {
      const timeline = await fetchTimeline(id, level);
      controls.loadTimeline(timeline);
      setKotlinCode(timeline.kotlinCode ?? '');
    } catch (e) {
      console.error('Failed to load scenario:', e);
    }
  }, [controls]);

  const handleSelectScenario = useCallback(async (id: string) => {
    setActiveScenarioId(id);
    loadScenario(id, activeLevel);
  }, [activeLevel, loadScenario]);

  const handleLevelChange = useCallback((level: 'beginner' | 'intermediate' | 'advanced') => {
    setActiveLevel(level);
    if (activeScenarioId && !customScenarios.some(s => s.scenario.id === activeScenarioId)) {
      loadScenario(activeScenarioId, level);
    }
  }, [activeScenarioId, customScenarios, loadScenario]);

  const handleSelectCustomScenario = useCallback((id: string) => {
    const found = customScenarios.find(s => s.scenario.id === id);
    if (!found) return;
    setActiveScenarioId(id);
    setSelectedNodeId(null);
    setContextMenu(null);
    controls.loadTimeline(found.timeline);
    setKotlinCode(found.timeline.kotlinCode ?? '');
  }, [controls, customScenarios]);

  const handleDeleteCustomScenario = useCallback((id: string) => {
    setCustomScenarios(prev => prev.filter(s => s.scenario.id !== id));
    if (activeScenarioId === id) {
      setActiveScenarioId(null);
    }
  }, [activeScenarioId]);

  const handleEditCustomScenario = useCallback((id: string) => {
    setEditingScenarioId(id);
    setShowBuilder(true);
  }, []);

  const handleBuilderGenerate = useCallback((scenario: CustomScenario, timeline: EventTimeline) => {
    if (editingScenarioId) {
      setCustomScenarios(prev => prev.map(s =>
        s.scenario.id === editingScenarioId ? { scenario, timeline } : s
      ));
    } else {
      setCustomScenarios(prev => [...prev, { scenario, timeline }]);
    }
    setActiveScenarioId(scenario.id);
    setSelectedNodeId(null);
    setShowBuilder(false);
    setEditingScenarioId(null);
    controls.loadTimeline(timeline);
    setKotlinCode(timeline.kotlinCode ?? '');
  }, [controls, editingScenarioId]);

  // Context menu handlers
  const handleNodeRightClick = useCallback((nodeId: string, screenX: number, screenY: number) => {
    setContextMenu({ nodeId, x: screenX, y: screenY });
  }, []);

  const handleCancel = useCallback((nodeId: string) => {
    const node = findNode(animState.layoutRoot, nodeId) ?? findNode(animState.secondLayoutRoot, nodeId);
    if (!node) return;
    const events = generateCancelEvents(node, animState.nodeStates);
    controls.injectEvents(events);
  }, [animState.layoutRoot, animState.secondLayoutRoot, animState.nodeStates, controls]);

  const handleInjectException = useCallback((nodeId: string, message: string) => {
    const node = findNode(animState.layoutRoot, nodeId) ?? findNode(animState.secondLayoutRoot, nodeId);
    if (!node || !animState.layoutRoot) return;
    const events = generateExceptionEvents(node, animState.nodeStates, message, animState.layoutRoot);
    controls.injectEvents(events);
  }, [animState.layoutRoot, animState.secondLayoutRoot, animState.nodeStates, controls]);

  const handleForceComplete = useCallback((nodeId: string) => {
    const node = findNode(animState.layoutRoot, nodeId) ?? findNode(animState.secondLayoutRoot, nodeId);
    if (!node) return;
    const events = generateForceCompleteEvents(node, animState.nodeStates);
    controls.injectEvents(events);
  }, [animState.layoutRoot, animState.secondLayoutRoot, animState.nodeStates, controls]);

  // Get context menu node info
  const contextMenuNode = contextMenu
    ? findNode(animState.layoutRoot, contextMenu.nodeId) ?? findNode(animState.secondLayoutRoot, contextMenu.nodeId)
    : null;
  const contextMenuNodeState = contextMenu ? animState.nodeStates.get(contextMenu.nodeId) : undefined;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: BG_COLOR }}>
      {/* Header */}
      <div style={{
        height: 44,
        background: SURFACE_COLOR,
        borderBottom: `1px solid ${BORDER_COLOR}`,
        display: 'flex',
        alignItems: 'center',
        padding: '0 16px',
        fontSize: 14,
        fontWeight: 600,
        letterSpacing: 1,
      }}>
        Coroutines Structured Concurrency Visualizer
      </div>

      {/* Main content */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left sidebar */}
        <ScenarioPanel
          activeScenarioId={activeScenarioId}
          onSelectScenario={handleSelectScenario}
          onCreateScenario={() => setShowBuilder(true)}
          customScenarios={customScenarios.map(s => ({ id: s.scenario.id, name: s.scenario.name }))}
          onSelectCustomScenario={handleSelectCustomScenario}
          onDeleteCustomScenario={handleDeleteCustomScenario}
          onEditCustomScenario={handleEditCustomScenario}
        />

        {/* Center canvas + code panel */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {/* Level tabs */}
          {activeScenarioId && !customScenarios.some(s => s.scenario.id === activeScenarioId) && (
            <div style={{
              display: 'flex',
              gap: 0,
              background: SURFACE_COLOR,
              borderBottom: `1px solid ${BORDER_COLOR}`,
              padding: '0 16px',
              flexShrink: 0,
            }}>
              {(['beginner', 'intermediate', 'advanced'] as const).map(level => (
                <button
                  key={level}
                  onClick={() => handleLevelChange(level)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    borderBottom: activeLevel === level ? '2px solid #7aa2f7' : '2px solid transparent',
                    color: activeLevel === level ? '#c0caf5' : '#565f89',
                    padding: '8px 16px',
                    fontSize: 12,
                    fontWeight: activeLevel === level ? 600 : 400,
                    cursor: 'pointer',
                    textTransform: 'capitalize',
                    letterSpacing: 0.5,
                    transition: 'color 0.15s, border-color 0.15s',
                  }}
                >
                  {level}
                </button>
              ))}
            </div>
          )}

          <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
            {animState.visualizationMode === 'timeline' ? (
              <TimelineCanvas
                layoutRoot={animState.layoutRoot}
                secondLayoutRoot={animState.secondLayoutRoot}
                nodeStates={animState.nodeStates}
                eventLog={animState.eventLog}
                timeline={animState.timeline}
                currentTimeMs={animState.currentTimeMs}
              />
            ) : (
              <>
                <TreeCanvas
                  layoutRoot={animState.layoutRoot}
                  secondLayoutRoot={animState.secondLayoutRoot}
                  nodeAnimations={animState.nodeAnimations}
                  waveAnimations={animState.waveAnimations}
                  selectedNodeId={selectedNodeId}
                  onNodeClick={setSelectedNodeId}
                  onNodeRightClick={handleNodeRightClick}
                  loadCounter={animState.loadCounter}
                />

                {/* Hint for right-click */}
                {animState.layoutRoot && animState.isPlaying && (
                  <div style={{
                    position: 'absolute',
                    bottom: 8,
                    right: 8,
                    fontSize: 10,
                    color: '#565f89',
                    background: '#24283bcc',
                    padding: '4px 8px',
                    borderRadius: 4,
                  }}>
                    Right-click an active node to manipulate it
                  </div>
                )}
              </>
            )}
          </div>

          <CodePanel kotlinCode={kotlinCode} />
        </div>

        {/* Right sidebar */}
        <div style={{
          width: 240,
          minWidth: 240,
          background: SURFACE_COLOR,
          borderLeft: `1px solid ${BORDER_COLOR}`,
          padding: 16,
          overflowY: 'auto',
        }}>
          <LegendPanel />
          <InfoPanel
            layoutRoot={animState.layoutRoot}
            secondLayoutRoot={animState.secondLayoutRoot}
            selectedNodeId={selectedNodeId}
            eventLog={animState.eventLog}
            nodeStates={animState.nodeStates}
          />
        </div>
      </div>

      {/* Bottom control bar */}
      <ControlPanel
        isPlaying={animState.isPlaying}
        currentEvent={animState.currentEventIndex}
        totalEvents={animState.totalEvents}
        speed={animState.speed}
        onPlay={controls.play}
        onPause={controls.pause}
        onStepForward={controls.stepForward}
        onStepBackward={controls.stepBackward}
        onReset={controls.reset}
        onSpeedChange={controls.setSpeed}
      />

      {/* Builder modal */}
      {showBuilder && (
        <ScenarioBuilderPanel
          onClose={() => { setShowBuilder(false); setEditingScenarioId(null); }}
          onGenerate={handleBuilderGenerate}
          editingScenario={editingScenarioId ? customScenarios.find(s => s.scenario.id === editingScenarioId)?.scenario : undefined}
        />
      )}

      {/* Context menu */}
      {contextMenu && contextMenuNode && contextMenuNodeState && (
        <NodeContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          nodeId={contextMenu.nodeId}
          nodeName={contextMenuNode.displayName}
          nodeState={contextMenuNodeState}
          onCancel={handleCancel}
          onInjectException={handleInjectException}
          onForceComplete={handleForceComplete}
          onClose={() => setContextMenu(null)}
        />
      )}
    </div>
  );
};

export default App;
