import { useState, useCallback, useRef } from 'react';
import { SimulationEvent, JobState } from '../types';
import { AnimationControls, OnBeforeEventCallback } from './useAnimationEngine';

export interface QuizQuestion {
  prompt: string;
  options: string[];
  correctIndex: number;
}

export interface QuizState {
  quizEnabled: boolean;
  phase: 'idle' | 'questioning' | 'answered';
  score: { correct: number; total: number };
  currentQuestion: QuizQuestion | null;
  selectedAnswer: number | null;
  isCorrect: boolean | null;
}

const STATE_LABELS: Record<JobState, string> = {
  New: 'is created (New)',
  Active: 'becomes Active',
  Suspended: 'becomes Suspended',
  Completing: 'enters Completing',
  Completed: 'is Completed',
  Cancelling: 'enters Cancelling',
  Cancelled: 'is Cancelled',
};

const ALL_STATES: JobState[] = ['New', 'Active', 'Suspended', 'Completing', 'Completed', 'Cancelling', 'Cancelled'];

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function generateQuestion(
  event: SimulationEvent,
  nodeStates: Map<string, JobState>,
): QuizQuestion | null {
  // Only quiz on meaningful events
  if (event.type === 'narrative') return null;

  const correctAnswer = event.description;

  const distractors: string[] = [];

  if (event.type === 'stateChange') {
    // Distractor 1: wrong state for same node
    const wrongStates = ALL_STATES.filter(s => s !== event.toState && s !== event.fromState);
    if (wrongStates.length > 0) {
      const wrongState = wrongStates[Math.floor(Math.random() * wrongStates.length)];
      const nodeName = correctAnswer.split(' ')[0];
      distractors.push(`${nodeName} ${STATE_LABELS[wrongState]}`);
    }

    // Distractor 2: different node, same transition
    const otherNodes = Array.from(nodeStates.entries())
      .filter(([id]) => id !== event.nodeId);
    if (otherNodes.length > 0) {
      const [, ] = otherNodes[Math.floor(Math.random() * otherNodes.length)];
      // Pick a random active node name from the states
      const randomNode = otherNodes[Math.floor(Math.random() * otherNodes.length)];
      // We don't have display names in the map, so construct from the ID
      distractors.push(`${randomNode[0]} ${STATE_LABELS[event.toState]}`);
    }

    // Distractor 3: opposite direction (complete instead of cancel or vice versa)
    if (event.toState === 'Cancelling' || event.toState === 'Cancelled') {
      const nodeName = correctAnswer.split(' ')[0];
      distractors.push(`${nodeName} enters Completing`);
    } else if (event.toState === 'Completing' || event.toState === 'Completed') {
      const nodeName = correctAnswer.split(' ')[0];
      distractors.push(`${nodeName} enters Cancelling`);
    }
  } else if (event.type === 'cancellation') {
    // Wrong direction
    distractors.push(
      `Cancellation propagates from ${event.targetNodeId} to ${event.sourceNodeId}`
    );
    // Different action
    distractors.push(
      `Exception propagates from ${event.sourceNodeId} to ${event.targetNodeId}`
    );
    // No effect
    distractors.push(
      `${event.targetNodeId} completes successfully`
    );
  } else if (event.type === 'exception') {
    distractors.push(
      `Exception propagates from ${event.targetNodeId} to ${event.sourceNodeId}`
    );
    distractors.push(
      `${event.sourceNodeId} catches the exception and continues`
    );
    distractors.push(
      `${event.targetNodeId} enters Completing`
    );
  }

  // Ensure we have at least 2 distractors, pad if needed
  while (distractors.length < 2) {
    distractors.push('Nothing happens — playback continues');
  }

  // Take up to 3 distractors, ensuring uniqueness
  const uniqueDistractors = [...new Set(distractors)]
    .filter(d => d !== correctAnswer)
    .slice(0, 3);

  const allOptions = [correctAnswer, ...uniqueDistractors];
  const shuffled = shuffle(allOptions);
  const correctIndex = shuffled.indexOf(correctAnswer);

  return {
    prompt: 'What happens next?',
    options: shuffled,
    correctIndex,
  };
}

export function useQuizMode() {
  const [quizEnabled, setQuizEnabled] = useState(false);
  const [phase, setPhase] = useState<'idle' | 'questioning' | 'answered'>('idle');
  const [score, setScore] = useState({ correct: 0, total: 0 });
  const [currentQuestion, setCurrentQuestion] = useState<QuizQuestion | null>(null);
  const [selectedAnswer, setSelectedAnswer] = useState<number | null>(null);
  const [isCorrect, setIsCorrect] = useState<boolean | null>(null);

  const controlsRef = useRef<AnimationControls | null>(null);
  const nodeStatesRef = useRef<Map<string, JobState>>(new Map());
  const pendingEventRef = useRef<{ event: SimulationEvent; index: number } | null>(null);

  const activate = useCallback((controls: AnimationControls) => {
    controlsRef.current = controls;
    setQuizEnabled(true);
    setScore({ correct: 0, total: 0 });
    setPhase('idle');

    const callback: OnBeforeEventCallback = (event, index) => {
      // Skip narrative events
      if (event.type === 'narrative') return true;

      const question = generateQuestion(event, nodeStatesRef.current);
      if (!question) return true;

      pendingEventRef.current = { event, index };
      setCurrentQuestion(question);
      setSelectedAnswer(null);
      setIsCorrect(null);
      setPhase('questioning');
      return false; // pause playback
    };

    controls.setOnBeforeEvent(callback);
  }, []);

  const deactivate = useCallback((controls: AnimationControls) => {
    controlsRef.current = null;
    setQuizEnabled(false);
    setPhase('idle');
    setCurrentQuestion(null);
    setSelectedAnswer(null);
    setIsCorrect(null);
    controls.setOnBeforeEvent(null);
    pendingEventRef.current = null;
  }, []);

  const submitAnswer = useCallback((answerIndex: number) => {
    if (!currentQuestion || phase !== 'questioning') return;
    const correct = answerIndex === currentQuestion.correctIndex;
    setSelectedAnswer(answerIndex);
    setIsCorrect(correct);
    setScore(prev => ({
      correct: prev.correct + (correct ? 1 : 0),
      total: prev.total + 1,
    }));
    setPhase('answered');
  }, [currentQuestion, phase]);

  const continuePlayback = useCallback(() => {
    setPhase('idle');
    setCurrentQuestion(null);
    setSelectedAnswer(null);
    setIsCorrect(null);
    pendingEventRef.current = null;

    // Resume playback
    if (controlsRef.current) {
      controlsRef.current.play();
    }
  }, []);

  const updateNodeStates = useCallback((states: Map<string, JobState>) => {
    nodeStatesRef.current = states;
  }, []);

  const state: QuizState = {
    quizEnabled,
    phase,
    score,
    currentQuestion,
    selectedAnswer,
    isCorrect,
  };

  return {
    state,
    activate,
    deactivate,
    submitAnswer,
    continuePlayback,
    updateNodeStates,
  };
}
