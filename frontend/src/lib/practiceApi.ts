import api from './api';

// ── Types ──────────────────────────────────────────────────────────────────

export type PracticeStatus = 'IDLE' | 'RUNNING' | 'STOPPED';

export interface PracticeSessionDto {
  id: number;
  name: string;
  eventId: number | null;
  eventName: string | null;
  status: PracticeStatus;
  bestLapN: number;
  /** How long a transponder may be quiet before its next lap starts a new run */
  pauseSeconds: number;
  startedAt: string | null;
  stoppedAt: string | null;
}

export interface PracticeTimingRowDto {
  position: number;
  transponderNumber: string;
  userId: number | null;
  racerName: string;
  laps: number;
  bestLapMs: number | null;
  bestConsecutiveNMs: number | null;
  lastLapMs: number | null;
  /** How many runs the driver has had; a long pause between laps starts a new one */
  runs: number;
  isUnknown: boolean;
}

export interface CreateSessionRequest {
  name: string;
  eventId?: number;
  bestLapN?: number;
  pauseSeconds?: number;
}

// ── API client ─────────────────────────────────────────────────────────────

/** Create a new practice session. */
export const createSession = (req: CreateSessionRequest) =>
  api.post<PracticeSessionDto>('/api/v1/practice-sessions', req).then(r => r.data);

/** Fetch a single practice session by ID. */
export const getSession = (id: number) =>
  api.get<PracticeSessionDto>(`/api/v1/practice-sessions/${id}`).then(r => r.data);

/** List recent practice sessions. */
export const listSessions = (limit = 10) =>
  api.get<PracticeSessionDto[]>(`/api/v1/practice-sessions?limit=${limit}`).then(r => r.data);

/** Transition a session from IDLE → RUNNING. */
export const startSession = (id: number) =>
  api.post<PracticeSessionDto>(`/api/v1/practice-sessions/${id}/start`).then(r => r.data);

/** Transition a session from RUNNING → STOPPED. */
export const stopSession = (id: number) =>
  api.post<PracticeSessionDto>(`/api/v1/practice-sessions/${id}/stop`).then(r => r.data);

/** Get the live snapshot (in-memory) for a running session. */
export const getSnapshot = (id: number) =>
  api.get<PracticeTimingRowDto[]>(`/api/v1/practice-sessions/${id}/snapshot`).then(r => r.data);

/** Get the final results for a stopped session. */
export const getResults = (id: number) =>
  api.get<PracticeTimingRowDto[]>(`/api/v1/practice-sessions/${id}/results`).then(r => r.data);
