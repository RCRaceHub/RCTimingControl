import api from './api';
import type { LiveTimingRowDto, QualifyingRoundPlace, ResultRow } from './raceControlApi';

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
  /** The colour the club picked for the class as #rrggbb, or null for none. */
  classColour?: string | null;
};

/** Auto run as a board shows it: a count to the next start, or why it is waiting. */
export type AutoRunBoardDto = {
  phase: 'WAITING' | 'RACING' | 'HELD';
  heldReason: 'FINALS' | 'NO_DRIVERS' | 'REFUSED' | null;
  /** When the next race starts, while waiting. */
  startAt: string | null;
  /** The server's clock when this was built. */
  serverTime: string;
};

export type NowNextDto = {
  eventId: number | null;
  eventName: string | null;
  currentRace: BoardRaceDto | null;
  nextRace: BoardRaceDto | null;
  lastCompletedRace: BoardRaceDto | null;
  /** Null unless automatic race progression is on for the event. */
  autoRun?: AutoRunBoardDto | null;
};

/** One driver in the combined result of an A final run in legs: lowest points wins. */
export type FinalLegsRow = {
  position: number;
  entryId: number;
  competitorId: number | null;
  driverName: string;
  carNumber: string | null;
  points: number;
  laps: number;
  timeMs: number;
  legPoints: number[];
  counted: boolean[];
};

export type FinalLegs = {
  eventClassId: number;
  className: string;
  legs: number;
  legsFinished: number;
  legsCounted: number;
  complete: boolean;
  rows: FinalLegsRow[];
};

export type ResultsBoardDto = {
  eventId: number | null;
  eventName: string | null;
  race: BoardRaceDto | null;
  results: ResultRow[];
  /** The A final's combined result when the race is one of its legs. */
  legs?: FinalLegs | null;
};

export type QualifyingBoardRow = {
  position: number;
  entryId: number;
  driverName: string;
  carNumber: string | null;
  lapsCompleted: number;
  bestLapMs: number | null;
  /** The low-points total when the class ranks qualifying round by round, else null. */
  points: number | null;
  /** The place in each finished round, in round order; empty unless the class ranks round by round. */
  rounds: QualifyingRoundPlace[];
  /** The heat, when the class's heats are separate overall races (positions count from 1 in each); else null. */
  heat?: number | null;
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

export type FinalsBoardFinal = {
  letter: string;
  status: BoardRaceDto['status'];
  /** The final was abandoned: no result is coming. */
  abandoned: boolean;
  /** The finished final's result, best first; the first leg's when the A final is run in legs. */
  results: ResultRow[];
  /** The A final's combined result when it is run in legs; it is the final's order. */
  legs: FinalLegs | null;
};

export type FinalsBoardClass = {
  eventClassId: number;
  className: string;
  finalsFinished: number;
  finalsTotal: number;
  finals: FinalsBoardFinal[];
};

export type FinalsBoardDto = {
  eventId: number | null;
  eventName: string | null;
  classes: FinalsBoardClass[];
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

/** One driver in the live qualifying table (#480). */
export type LiveQualifyingRow = {
  position: number;
  /** The place before the heat counted; null for a driver with no earlier result. */
  previousPosition: number | null;
  entryId: number;
  driverName: string;
  carNumber: string | null;
  points: number | null;
  lapsCompleted: number;
  bestLapMs: number | null;
  /** The driver is in the heat on track (or the one that just ended). */
  live: boolean;
  /** The driver's heat in this round has not run yet. */
  waiting: boolean;
};

export type LiveQualifyingDto = {
  eventId: number | null;
  eventName: string | null;
  /** LIVE while the heat runs (a projection), FINISHED once it has ended, NONE with nothing to show. */
  phase: 'LIVE' | 'FINISHED' | 'NONE';
  raceId: number | null;
  raceLabel: string | null;
  className: string | null;
  rows: LiveQualifyingRow[];
};

export async function getLiveQualifying(eventId?: number | null): Promise<LiveQualifyingDto> {
  const { data } = await api.get<LiveQualifyingDto>('/api/v1/boards/live-qualifying', eventParams(eventId));
  return data;
}

export async function getFinalsBoard(eventId?: number | null): Promise<FinalsBoardDto> {
  const { data } = await api.get<FinalsBoardDto>('/api/v1/boards/finals', eventParams(eventId));
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

/** Time left before the race is closed by force; null while no limit is counting down. */
export type ForcedCloseDto = {
  closesInMs: number | null;
};

export async function getForcedClose(raceId: number): Promise<ForcedCloseDto> {
  const { data } = await api.get<ForcedCloseDto>(`/api/v1/boards/races/${raceId}/closes-in`);
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

/** One car's place at the end of one lap of a race on track, for the live position chart. */
export type LapPlace = {
  lap: number;
  entryId: number;
  position: number;
};

export async function getPositionHistory(raceId: number): Promise<LapPlace[]> {
  const { data } = await api.get<LapPlace[]>(`/api/v1/boards/races/${raceId}/position-history`);
  return data;
}

/** What the overlay's ticker scrolls besides the live stats; names are as the public may see them. */
export type TickerDto = {
  nextRace: string | null;
  nextDrivers: string[];
  marshals: string[];
  /** The drivers marshalling the race on track. */
  currentMarshals: string[];
  /** True while a bump-up final waits on the final below, so who marshals it isn't known yet. */
  marshalsPending: boolean;
  topQualifiers: { className: string; driverName: string; bestLapMs: number | null }[];
};

export async function getTicker(eventId?: number | null): Promise<TickerDto> {
  const { data } = await api.get<TickerDto>('/api/v1/boards/ticker', eventParams(eventId));
  return data;
}
