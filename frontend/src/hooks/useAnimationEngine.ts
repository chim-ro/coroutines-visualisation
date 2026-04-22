import { useState, useRef, useCallback, useEffect } from 'react';
import { EventTimeline, SimulationEvent, StateChangeEvent, LayoutNode, JobState } from '../types';
import { NodeAnimation } from '../rendering/nodeRenderer';
import { WaveAnimation } from '../rendering/waveRenderer';
import { layoutTree, flattenTree } from '../rendering/treeLayout';

export interface AnimationState {
  layoutRoot: LayoutNode | null;
  secondLayoutRoot: LayoutNode | null;
  nodeStates: Map<string, JobState>;
  currentEventIndex: number;
  totalEvents: number;
  isPlaying: boolean;
  speed: number;
  nodeAnimations: NodeAnimation[];
  waveAnimations: WaveAnimation[];
  eventLog: SimulationEvent[];
  visualizationMode: string;
  currentTimeMs: number;
  timeline: EventTimeline | null;
  loadCounter: number;
}

export interface AnimationControls {
  play: () => void;
  pause: () => void;
  stepForward: () => void;
  stepBackward: () => void;
  reset: () => void;
  setSpeed: (speed: number) => void;
  loadTimeline: (timeline: EventTimeline) => void;
  injectEvents: (events: SimulationEvent[]) => void;
  getNodeStates: () => Map<string, JobState>;
  getTimeline: () => EventTimeline | null;
}

const FLASH_DURATION = 600;
const WAVE_DURATION = 400;

export function useAnimationEngine(): [AnimationState, AnimationControls] {
  const [timeline, setTimeline] = useState<EventTimeline | null>(null);
  const [currentEventIndex, setCurrentEventIndex] = useState(-1);
  const [isPlaying, setIsPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [nodeStates, setNodeStates] = useState<Map<string, JobState>>(new Map());
  const [layoutRoot, setLayoutRoot] = useState<LayoutNode | null>(null);
  const [secondLayoutRoot, setSecondLayoutRoot] = useState<LayoutNode | null>(null);
  const [nodeAnimations, setNodeAnimations] = useState<NodeAnimation[]>([]);
  const [waveAnimations, setWaveAnimations] = useState<WaveAnimation[]>([]);
  const [eventLog, setEventLog] = useState<SimulationEvent[]>([]);
  const [visualizationMode, setVisualizationMode] = useState('tree');
  const [loadCounter, setLoadCounter] = useState(0);

  const playTimeoutRef = useRef<number | null>(null);
  const animFrameRef = useRef<number | null>(null);

  const initFromTimeline = useCallback((tl: EventTimeline) => {
    const states = new Map<string, JobState>();
    const collectStates = (node: { id: string; initialState: JobState; children: any[] }) => {
      states.set(node.id, node.initialState);
      node.children.forEach(collectStates);
    };
    collectStates(tl.tree);
    if (tl.secondTree) collectStates(tl.secondTree);
    setNodeStates(states);

    const root = layoutTree(tl.tree, 50);
    setLayoutRoot(root);

    if (tl.secondTree) {
      // For second tree, offset it to the right
      const root2 = layoutTree(tl.secondTree, 450);
      setSecondLayoutRoot(root2);
    } else {
      setSecondLayoutRoot(null);
    }

    setCurrentEventIndex(-1);
    setNodeAnimations([]);
    setWaveAnimations([]);
    setEventLog([]);
    setIsPlaying(false);
  }, []);

  const applyEvent = useCallback((event: SimulationEvent, index: number) => {
    const now = performance.now();

    setEventLog(prev => [...prev, event]);

    if (event.type === 'stateChange') {
      setNodeStates(prev => {
        const next = new Map(prev);
        next.set(event.nodeId, event.toState);
        return next;
      });
      setNodeAnimations(prev => [
        ...prev.filter(a => a.nodeId !== event.nodeId || a.type !== 'flash'),
        { nodeId: event.nodeId, type: 'flash', startTime: now, duration: FLASH_DURATION },
      ]);
    } else if (event.type === 'cancellation') {
      setWaveAnimations(prev => [
        ...prev,
        {
          id: `cancel-${index}`,
          type: 'cancellation',
          fromNodeId: event.sourceNodeId,
          toNodeId: event.targetNodeId,
          startTime: now,
          duration: WAVE_DURATION,
        },
      ]);
    } else if (event.type === 'exception') {
      setWaveAnimations(prev => [
        ...prev,
        {
          id: `exception-${index}`,
          type: 'exception',
          fromNodeId: event.sourceNodeId,
          toNodeId: event.targetNodeId,
          startTime: now,
          duration: WAVE_DURATION,
        },
      ]);
    }

    setCurrentEventIndex(index);
  }, []);

  const applyEventsUpTo = useCallback((targetIndex: number, tl: EventTimeline) => {
    const states = new Map<string, JobState>();
    const collectStates = (node: { id: string; initialState: JobState; children: any[] }) => {
      states.set(node.id, node.initialState);
      node.children.forEach(collectStates);
    };
    collectStates(tl.tree);
    if (tl.secondTree) collectStates(tl.secondTree);

    const log: SimulationEvent[] = [];

    for (let i = 0; i <= targetIndex && i < tl.events.length; i++) {
      const event = tl.events[i];
      log.push(event);
      if (event.type === 'stateChange') {
        states.set(event.nodeId, event.toState);
      }
    }

    setNodeStates(states);
    setEventLog(log);
    setCurrentEventIndex(targetIndex);
    setNodeAnimations([]);
    setWaveAnimations([]);
  }, []);

  // Playback loop
  useEffect(() => {
    if (!isPlaying || !timeline) return;

    const nextIndex = currentEventIndex + 1;
    if (nextIndex >= timeline.events.length) {
      setIsPlaying(false);
      return;
    }

    const currentDelay = currentEventIndex >= 0 ? timeline.events[currentEventIndex].delayMs : 0;
    const nextDelay = timeline.events[nextIndex].delayMs;
    const waitMs = (nextDelay - currentDelay) / speed;

    playTimeoutRef.current = window.setTimeout(() => {
      applyEvent(timeline.events[nextIndex], nextIndex);
    }, Math.max(waitMs, 50));

    return () => {
      if (playTimeoutRef.current) clearTimeout(playTimeoutRef.current);
    };
  }, [isPlaying, currentEventIndex, timeline, speed, applyEvent]);

  // Update layout nodes with current states
  useEffect(() => {
    if (!layoutRoot) return;
    const updateStates = (node: LayoutNode) => {
      const state = nodeStates.get(node.id);
      if (state) node.state = state;
      node.children.forEach(updateStates);
    };
    updateStates(layoutRoot);
    if (secondLayoutRoot) updateStates(secondLayoutRoot);
  }, [nodeStates, layoutRoot, secondLayoutRoot]);

  const timelineRef = useRef<EventTimeline | null>(null);
  const nodeStatesRef = useRef<Map<string, JobState>>(new Map());
  const currentEventIndexRef = useRef(-1);

  // Keep refs in sync
  useEffect(() => { timelineRef.current = timeline; }, [timeline]);
  useEffect(() => { nodeStatesRef.current = nodeStates; }, [nodeStates]);
  useEffect(() => { currentEventIndexRef.current = currentEventIndex; }, [currentEventIndex]);

  const replaceTimeline = useCallback((tl: EventTimeline) => {
    // Update timeline without resetting playback state
    setTimeline(tl);
  }, []);

  const controls: AnimationControls = {
    play: () => setIsPlaying(true),
    pause: () => setIsPlaying(false),
    stepForward: () => {
      if (!timeline) return;
      setIsPlaying(false);
      const nextIndex = currentEventIndex + 1;
      if (nextIndex < timeline.events.length) {
        applyEvent(timeline.events[nextIndex], nextIndex);
      }
    },
    stepBackward: () => {
      if (!timeline) return;
      setIsPlaying(false);
      const prevIndex = currentEventIndex - 1;
      if (prevIndex >= -1) {
        if (prevIndex === -1) {
          initFromTimeline(timeline);
        } else {
          applyEventsUpTo(prevIndex, timeline);
        }
      }
    },
    reset: () => {
      if (!timeline) return;
      setIsPlaying(false);
      initFromTimeline(timeline);
    },
    setSpeed: (s: number) => setSpeed(s),
    loadTimeline: (tl: EventTimeline) => {
      setTimeline(tl);
      setVisualizationMode(tl.visualizationMode ?? 'tree');
      setLoadCounter(c => c + 1);
      initFromTimeline(tl);
    },
    injectEvents: (events: SimulationEvent[]) => {
      const tl = timelineRef.current;
      if (!tl || events.length === 0) return;

      const idx = currentEventIndexRef.current;
      // Compute base delayMs: offset new events relative to current position
      const baseDelay = idx >= 0 && idx < tl.events.length
        ? tl.events[idx].delayMs
        : 0;

      // Assign absolute delayMs to injected events
      const offsetEvents = events.map((e, i) => ({
        ...e,
        delayMs: baseDelay + e.delayMs + (i * 50), // stagger by 50ms each
      }));

      // Collect node IDs being cancelled/completed by injected events
      // so we can remove conflicting future events for those nodes
      const affectedNodeIds = new Set<string>();
      for (const e of offsetEvents) {
        if (e.type === 'stateChange') {
          const sc = e as StateChangeEvent;
          if (sc.toState === 'Cancelling' || sc.toState === 'Cancelled' ||
              sc.toState === 'Completing' || sc.toState === 'Completed') {
            affectedNodeIds.add(sc.nodeId);
          }
        }
      }

      // Splice into the remaining events after current index
      const before = tl.events.slice(0, idx + 1);
      const after = tl.events.slice(idx + 1);

      // Filter out future events that target cancelled/completed nodes
      const filtered = after.filter(e => {
        if (e.type === 'stateChange') {
          return !affectedNodeIds.has((e as StateChangeEvent).nodeId);
        }
        return true;
      });

      // Merge offset events with remaining events, sorted by delayMs
      const merged = [...offsetEvents, ...filtered].sort((a, b) => a.delayMs - b.delayMs);

      const newTimeline: EventTimeline = {
        ...tl,
        events: [...before, ...merged],
      };

      replaceTimeline(newTimeline);
    },
    getNodeStates: () => new Map(nodeStatesRef.current),
    getTimeline: () => timelineRef.current,
  };

  const currentTimeMs = timeline && currentEventIndex >= 0 && currentEventIndex < timeline.events.length
    ? timeline.events[currentEventIndex].delayMs
    : 0;

  const state: AnimationState = {
    layoutRoot,
    secondLayoutRoot,
    nodeStates,
    currentEventIndex,
    totalEvents: timeline?.events.length ?? 0,
    isPlaying,
    speed,
    nodeAnimations,
    waveAnimations,
    eventLog,
    visualizationMode,
    currentTimeMs,
    timeline,
    loadCounter,
  };

  return [state, controls];
}
