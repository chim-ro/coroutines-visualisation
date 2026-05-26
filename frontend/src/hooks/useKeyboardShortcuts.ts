import { useEffect } from 'react';
import { AnimationControls } from './useAnimationEngine';

const SPEED_PRESETS = [0.25, 0.5, 1, 1.5, 2];

export function useKeyboardShortcuts(controls: AnimationControls, isPlaying: boolean) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) {
        return;
      }

      switch (e.code) {
        case 'Space':
          e.preventDefault();
          if (isPlaying) controls.pause();
          else controls.play();
          break;
        case 'ArrowRight':
          e.preventDefault();
          controls.stepForward();
          break;
        case 'ArrowLeft':
          e.preventDefault();
          controls.stepBackward();
          break;
        case 'KeyR':
          controls.reset();
          break;
        case 'Digit1':
        case 'Digit2':
        case 'Digit3':
        case 'Digit4':
        case 'Digit5': {
          const idx = parseInt(e.code.charAt(5), 10) - 1;
          controls.setSpeed(SPEED_PRESETS[idx]);
          break;
        }
      }
    };

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [controls, isPlaying]);
}
