import { ScenarioInfo, EventTimeline } from '../types';

const BASE_URL = '/api';

export async function fetchScenarios(): Promise<ScenarioInfo[]> {
  const res = await fetch(`${BASE_URL}/scenarios`);
  if (!res.ok) throw new Error(`Failed to fetch scenarios: ${res.status}`);
  return res.json();
}

export async function fetchTimeline(scenarioId: string, level?: string): Promise<EventTimeline> {
  const params = level ? `?level=${level}` : '';
  const res = await fetch(`${BASE_URL}/scenarios/${scenarioId}${params}`);
  if (!res.ok) throw new Error(`Failed to fetch timeline: ${res.status}`);
  return res.json();
}
