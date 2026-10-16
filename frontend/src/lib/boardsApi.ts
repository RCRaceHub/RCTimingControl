import api from './api';
import type { LiveTimingRowDto, ResultRow } from './raceControlApi';

// Anonymous spectator board endpoints (L12). Every call here is public, so a board never
// trips the 401 → /login redirect in the shared axios client.

export type BoardRaceDto = {
  raceId: number;
  label: string;
  roundType: string;
  roundNumber: number;
  className: string;
  heatNumber: number;
  finalLetter: string | null;
  status: 'PENDING' | 'GRID' | 'RUNNING' | 'STOPPED' | 'FINISHED';
};

export type NowNextDto = {
  eventId: number | null;
  eventName: string | null;
  currentRace: BoardRaceDto | null;
  nextRace: BoardRaceDto | null;
  lastCompletedRace: BoardRaceDto | null;
};

export type ResultsBoardDto = {
  eventId: number | null;
  eventName: string | null;
  race: BoardRaceDto | null;
  results: ResultRow[];
};

export type QualifyingBoardRow = {
  position: number;
  entryId: number;
  driverName: string;
  lapsCompleted: number;
  bestLapMs: number | null;
  /** The low-points total when the class ranks qualifying round by round, else null. */
  points: number | null;
};

export type QualifyingBoardTable = {
  eventClassId: number;
  className: string;
  heatsFinished: number;
  heatsTotal: number;
  finalsStarted: boolean;
  rows: QualifyingBoardRow[];
};

export type QualifyingBoardDto = {
  eventId: number | null;
  eventName: string | null;
  tables: QualifyingBoardTable[];
};

function eventParams(eventId: number | null | undefined) {
  return eventId ? { params: { eventId } } : undefined;
}

export async function getNowNext(eventId?: number | null): Promise<NowNextDto> {
  const { data } = await api.get<NowNextDto>('/api/v1/boards/now-next', eventParams(eventId));
  return data;
}

export async function getResultsBoard(eventId?: number | null): Promise<ResultsBoardDto> {
  const { data } = await api.get<ResultsBoardDto>('/api/v1/boards/results', eventParams(eventId));
  return data;
}

export async function getQualifyingBoard(eventId?: number | null): Promise<QualifyingBoardDto> {
  const { data } = await api.get<QualifyingBoardDto>('/api/v1/boards/qualifying', eventParams(eventId));
  return data;
}

export async function getBoardLiveTiming(raceId: number): Promise<LiveTimingRowDto[]> {
  const { data } = await api.get<LiveTimingRowDto[]>(`/api/v1/boards/races/${raceId}/live-timing`);
  return data;
}

/** A race's clock: race time so far without stoppages, its length from the format, and whether it is counting. */
export type RaceClockDto = {
  raceId: number;
  status: BoardRaceDto['status'];
  elapsedMs: number;
  durationMs: number | null;
  remainingMs: number | null;
  running: boolean;
};

export async function getRaceClock(raceId: number): Promise<RaceClockDto> {
  const { data } = await api.get<RaceClockDto>(`/api/v1/boards/races/${raceId}/clock`);
  return data;
}

/** The fastest lap so far in the other finished heats of a race's round and class; both null when there is none. */
export type RoundFastestLapDto = {
  lapMs: number | null;
  driverName: string | null;
};

export async function getRoundFastestLap(raceId: number): Promise<RoundFastestLapDto> {
  const { data } = await api.get<RoundFastestLapDto>(`/api/v1/boards/races/${raceId}/round-fastest-lap`);
  return data;
}
