import { JobState } from '../types';

export const STATE_COLORS: Record<JobState, string> = {
  New: '#565f89',
  Active: '#9ece6a',
  Suspended: '#bb9af7',
  Completing: '#e0af68',
  Completed: '#7aa2f7',
  Cancelling: '#ff9e64',
  Cancelled: '#f7768e',
};

export const BG_COLOR = '#1a1b26';
export const SURFACE_COLOR = '#24283b';
export const BORDER_COLOR = '#414868';
export const TEXT_COLOR = '#c0caf5';
export const TEXT_DIM = '#565f89';
export const ACCENT_COLOR = '#7aa2f7';

export const CANCELLATION_WAVE_COLOR = '#ff9e64';
export const EXCEPTION_WAVE_COLOR = '#f7768e';
