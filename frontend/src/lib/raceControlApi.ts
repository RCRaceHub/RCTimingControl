import api from './api';
import type { StandingsRowDto } from './adminApi';

// ── Types ──────────────────────────────────────────────────────────────────

export type GridCallSlotDto = {
  gridPosition: number;
  entryId: number;
  driverName: string;
  /** What the announcer says for this driver: their spoken name, else the name tidied for speech (#119, #120). */
  speechName: string;
  carNumber: string | null;
  className: string;
  /** Checked in at the desk (L11). */
  checkedIn: boolean;
  /** RaceHub's arrival mark, read-only; null when the entry wasn't imported. */
  racehubArrival: 'ARRIVED' | 'NOT_ARRIVED' | null;
};

export type MarshalDutyRowDto = {
  entryId: number;
  driverName: string;
  carNumber: string | null;
  missedThisEvent: number;
};

export type PreRaceReadinessDto = {
  raceId: number;
  raceLabel: string;
  firstRaceOfEvent: boolean;
  gridCall: GridCallSlotDto[];
  marshalDuty: MarshalDutyRowDto[];
};

export type RunOrderItemDto = {
  raceId: number;
  sequenceInEvent: number;
  roundNumber: number;
  roundType: 'PRACTICE' | 'QUALIFIER' | 'FINAL';
  className: string;
  heatNumber: number;
  finalLetter: string | null;
  status: 'PENDING' | 'GRID' | 'RUNNING' | 'STOPPED' | 'FINISHED';
  sequenceInRound: number;
  startedAt: string | null;
};

export type ResultRow = {
  position: number;
  entryId: number;
  driverName: string;
  carNumber: string | null;
  lapsCompleted: number;
  totalTimeMs: number;
  bestLapMs: number | null;
  gapToLeaderMs: number | null;
};

export type PositionAtLap = {
  lapNumber: number;
  entryId: number;
  position: number;
  lapTimeMs?: number | null;
};

export type ClubBrandingDto = {
  clubName: string;
  logoUrl: string | null;
};

export type ResultSnapshotDto = {
  raceId: number;
  raceLabel: string;
  finishedAt: string;
  positions: ResultRow[];
  lapHistory: PositionAtLap[];
  clubBranding: ClubBrandingDto | null;
};

export type LiveTimingRowDto = {
  entryId: number;
  driverName: string;
  position: number;
  lapsCompleted: number;
  lastPassingTimeMs: number;
  lastLapMs: number | null;
  bestLapMs: number | null;
  avgLapMs: number | null;
  overallFastestLapMs: number | null;
  lapsDown: number;
  intervalLapsDown: number;
  gapToLeaderMs: number | null;
  gapToAheadMs: number | null;
};

export type RaceStateChangeDto = {
  raceId: number;
  newStatus: string;
};

export type MarshalAdjustmentRequest = {
  entryId: number;
  transponderNumber: string;
  lapDelta: number;
};

export type PenaltyRequest = {
  entryId: number;
  penaltyType: 'LAP' | 'TIME';
  value: number;
  reason: string;
};

export type IncidentReportRequest = {
  entryId: number;
  incidentType: string;
  description: string;
};

export type MarshalAbsenceRequest = {
  entryId: number;
  eventId: number;
  notes?: string;
};

// ── API client ─────────────────────────────────────────────────────────────

export async function getPreRaceReadiness(raceId: number): Promise<PreRaceReadinessDto> {
  const { data } = await api.get<PreRaceReadinessDto>(
    `/api/v1/race-control/race/${raceId}/pre-race-readiness`,
  );
  return data;
}

export async function getRunOrder(eventId: number): Promise<RunOrderItemDto[]> {
  const { data } = await api.get<RunOrderItemDto[]>(
    `/api/v1/race-control/event/${eventId}/run-order`,
  );
  return data;
}

export async function callGrid(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/call-grid`);
}

export async function startRace(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/start`);
}

export async function stopRace(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/stop`);
}

export async function finishRace(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/finish`);
}

export async function abandonRace(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/abandon`);
}

export async function restartRace(raceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/restart`);
}

export async function skipToRace(raceId: number, targetRaceId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/skip-to`, { targetRaceId });
}

export async function marshalAdjustment(
  raceId: number,
  req: MarshalAdjustmentRequest,
): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/marshal-adjustment`, req);
}

export async function raiseIncident(raceId: number, req: IncidentReportRequest): Promise<void> {
  await api.post(`/api/v1/race-control/referee/race/${raceId}/incident-report`, req);
}

export async function applyPenalty(raceId: number, req: PenaltyRequest): Promise<void> {
  await api.post(`/api/v1/race-control/referee/race/${raceId}/penalty`, req);
}

export async function recordMarshalAbsent(
  raceId: number,
  req: MarshalAbsenceRequest,
): Promise<void> {
  await api.post(`/api/v1/race-control/referee/race/${raceId}/marshal-absent`, req);
}

// ── Unknown transponder linking ──────────────────────────────────────────────

export type RaceEntryDto = {
  entryId: number;
  racerName: string;
  carNumber: string | null;
};

export type LinkTransponderRequest = {
  transponderNumber: string;
  entryId: number;
};

export type LinkTransponderResponse = {
  lapsCredited: number;
};

export async function getRaceEntries(raceId: number): Promise<RaceEntryDto[]> {
  const { data } = await api.get<RaceEntryDto[]>(`/api/v1/race-control/race/${raceId}/entries`);
  return data;
}

export async function linkUnknownTransponder(
  raceId: number,
  transponderNumber: string,
  entryId: number,
): Promise<LinkTransponderResponse> {
  const { data } = await api.post<LinkTransponderResponse>(
    `/api/v1/race-control/race/${raceId}/transponders/link`,
    { transponderNumber, entryId },
  );
  return data;
}

// ── Public (no-auth) API functions ───────────────────────────────────────────

export async function getPublicResultSnapshot(raceId: number): Promise<ResultSnapshotDto> {
  const { data } = await api.get<ResultSnapshotDto>(`/api/v1/results/${raceId}`);
  return data;
}

export async function getPublicChampionshipStandings(
  championshipId: number,
): Promise<StandingsRowDto[]> {
  const { data } = await api.get<StandingsRowDto[]>(`/api/v1/championships/${championshipId}`);
  return data;
}

export type EventScheduleDto = {
  id: number;
  name: string;
  eventDate: string;
  entryAvailability: 'ENTRY_OPEN' | 'ENTRY_NOT_YET_OPEN' | 'ENTRY_CLOSED';
  finishedRaceIds: number[];
  championshipId: number | null;
};

export async function getEventSchedule(): Promise<EventScheduleDto[]> {
  const { data } = await api.get<EventScheduleDto[]>('/api/v1/events');
  return data;
}

// ── Decoder status ─────────────────────────────────────────────────────────

export type ConnectionState = 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED';

export type DecoderStatusDto = {
  decoderState: ConnectionState;
};

export async function fetchDecoderStatus(): Promise<DecoderStatusDto> {
  const { data } = await api.get<DecoderStatusDto>('/api/v1/race-control/decoder/status');
  return data;
}

// ── Live feed for remote viewers (#28) ───────────────────────────────────────

export type LiveFeedState = 'NOT_SET_UP' | 'IDLE' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';

export type LiveFeedStatusDto = {
  state: LiveFeedState;
  /** The relay's host name; null when no relay is set. */
  relayHost: string | null;
  missingSettings: string[];
};

export type LiveFeedSettingDto = {
  enabled: boolean;
};

export async function fetchLiveFeedStatus(): Promise<LiveFeedStatusDto> {
  const { data } = await api.get<LiveFeedStatusDto>('/api/v1/race-control/live-feed/status');
  return data;
}

export async function getLiveFeedSetting(eventId: number): Promise<LiveFeedSettingDto> {
  const { data } = await api.get<LiveFeedSettingDto>(`/api/v1/race-control/events/${eventId}/live-feed`);
  return data;
}

export async function setLiveFeedSetting(eventId: number, enabled: boolean): Promise<LiveFeedSettingDto> {
  const { data } = await api.put<LiveFeedSettingDto>(`/api/v1/race-control/events/${eventId}/live-feed`, { enabled });
  return data;
}

// ── Check-in desk and transponder swap (L11) ─────────────────────────────────

export type CheckInEntry = {
  entryId: number;
  competitorName: string;
  /** For fixing how the name is said at the desk (#119). */
  competitorId: number | null;
  /** The override for how the name is said aloud; null means none. */
  spokenName: string | null;
  /** What the announcer says for this driver: the spoken name, else the name tidied (#120). */
  speechName: string;
  className: string | null;
  transponderNumber: string;
  secondaryTransponderNumber: string | null;
  checkedIn: boolean;
  checkedInAt: string | null;
  racehubArrival: 'ARRIVED' | 'NOT_ARRIVED' | null;
  /** The imported file's numbers where they differ from a transponder swapped on the day (#50) */
  importedTransponderNumber: string | null;
  importedSecondaryTransponderNumber: string | null;
};

export type CheckInConfirmResponse = {
  entry: CheckInEntry;
  alreadyCheckedIn: boolean;
};

export type TransponderSlot = 'PRIMARY' | 'SECONDARY';

export type TransponderSwapResponse = {
  entryId: number;
  slot: TransponderSlot;
  oldTransponderNumber: string | null;
  newTransponderNumber: string | null;
};

/** Entries using this transponder number; a 404 means no match. */
export async function checkInResolve(eventId: number, transponderNumber: string): Promise<CheckInEntry[]> {
  const { data } = await api.post<CheckInEntry[]>(
    `/api/v1/race-control/events/${eventId}/check-in/resolve`,
    { transponderNumber },
  );
  return data;
}

export async function checkInSearch(eventId: number, query: string): Promise<CheckInEntry[]> {
  const { data } = await api.get<CheckInEntry[]>(
    `/api/v1/race-control/events/${eventId}/check-in/search`,
    { params: { query } },
  );
  return data;
}

export async function checkInConfirm(eventId: number, entryId: number): Promise<CheckInConfirmResponse> {
  const { data } = await api.post<CheckInConfirmResponse>(
    `/api/v1/race-control/events/${eventId}/check-in/entries/${entryId}/confirm`,
  );
  return data;
}

/** A blank number removes the secondary transponder. */
export async function swapTransponder(
  eventId: number,
  entryId: number,
  slot: TransponderSlot,
  newTransponderNumber: string,
): Promise<TransponderSwapResponse> {
  const { data } = await api.post<TransponderSwapResponse>(
    `/api/v1/race-control/events/${eventId}/entries/${entryId}/transponder-swap`,
    { slot, newTransponderNumber },
  );
  return data;
}

export type RaceHistoryKind =
  | 'LIFECYCLE'
  | 'PENALTY'
  | 'INCIDENT'
  | 'MARSHAL_LAP'
  | 'MARSHAL_ABSENCE'
  | 'MARSHAL_PENALTY'
  | 'TRANSPONDER_LINK'
  | 'OTHER';

export type RaceHistoryItem = {
  at: string;
  kind: RaceHistoryKind;
  actor: string | null;
  driver: string | null;
  summary: string;
};

/** Everything that happened in a race, oldest first (#140). */
export async function getRaceHistory(raceId: number): Promise<RaceHistoryItem[]> {
  const { data } = await api.get<RaceHistoryItem[]>(`/api/v1/race-control/race/${raceId}/history`);
  return data;
}
