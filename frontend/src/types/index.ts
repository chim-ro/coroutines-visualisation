export type JobState = 'New' | 'Active' | 'Suspended' | 'Completing' | 'Completed' | 'Cancelling' | 'Cancelled';

export type BuilderType = 'Launch' | 'Async' | 'CoroutineScope' | 'SupervisorScope' | 'RunBlocking';

export type JobType = 'Job' | 'SupervisorJob';

export interface CoroutineNode {
  id: string;
  displayName: string;
  builder: BuilderType;
  jobType: JobType;
  initialState: JobState;
  children: CoroutineNode[];
}

export interface StateChangeEvent {
  type: 'stateChange';
  delayMs: number;
  description: string;
  nodeId: string;
  fromState: JobState;
  toState: JobState;
}

export interface CancellationEvent {
  type: 'cancellation';
  delayMs: number;
  description: string;
  sourceNodeId: string;
  targetNodeId: string;
}

export interface ExceptionEvent {
  type: 'exception';
  delayMs: number;
  description: string;
  sourceNodeId: string;
  targetNodeId: string;
  exceptionMessage: string;
}

export interface NarrativeEvent {
  type: 'narrative';
  delayMs: number;
  description: string;
}

export type SimulationEvent = StateChangeEvent | CancellationEvent | ExceptionEvent | NarrativeEvent;

export interface ThreadSegment {
  taskId: string;
  taskName: string;
  startMs: number;
  endMs: number;
  state: string;  // "active", "blocked", "suspended"
}

export interface ThreadLane {
  threadName: string;
  segments: ThreadSegment[];
}

export interface EventTimeline {
  scenarioName: string;
  tree: CoroutineNode;
  secondTree: CoroutineNode | null;
  events: SimulationEvent[];
  kotlinCode: string;
  visualizationMode?: string;
  leftThreadLanes?: ThreadLane[] | null;
  rightThreadLanes?: ThreadLane[] | null;
  totalDurationMs?: number;
}

export interface ScenarioInfo {
  id: string;
  name: string;
  description: string;
  category: string;
}

// Builder types
export interface FailureConfig {
  exceptionMessage: string;
  timingMs: number;
}

export interface BuilderNodeConfig {
  id: string;
  displayName: string;
  builder: BuilderType;
  jobType: JobType;
  children: BuilderNodeConfig[];
  failure?: FailureConfig;
}

// Layout types for canvas rendering
export interface LayoutNode {
  id: string;
  displayName: string;
  builder: BuilderType;
  jobType: JobType;
  state: JobState;
  x: number;
  y: number;
  children: LayoutNode[];
  parent: LayoutNode | null;
}
