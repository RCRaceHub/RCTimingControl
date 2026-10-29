import api from './api';
import type { StandingsRowDto } from './adminApi';
import type { FinalLegs } from './boardsApi';

// ── Types ──────────────────────────────────────────────────────────────────

export type GridCallSlotDto = {
  gridPosition: number;
  entryId: number;
  driverName: string;
  /** What the announcer says for this driver: their spoken name, else the name tidied for speech (#119, #120). */
  speechName: string;
  carNumber: string | null;
  className: string;
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
  eventId: number;
  raceLabel: string;
  firstRaceOfEvent: boolean;
  gridCall: GridCallSlotDto[];
  marshalDuty: MarshalDutyRowDto[];
  /** True while the final below a bump-up final is unfinished: who moves up, and so can't marshal, isn't known. */
  marshalsPending: boolean;
  startType: 'STAGGER' | 'GRID' | 'ROLLING' | null;
  staggerIntervalSeconds: number;
  /** The entries the decoder has seen on the warm-up lap while the race is on the grid (#482). */
  seenEntryIds?: number[];
};

export type RunOrderItemDto = {
  raceId: number;
  eventClassId: number;
  sequenceInEvent: number;
  roundNumber: number;
  roundType: 'PRACTICE' | 'QUALIFIER' | 'FINAL';
  className: string;
  heatNumber: number;
  finalLetter: string | null;
  status: 'PENDING' | 'GRID' | 'RUNNING' | 'STOPPED' | 'FINISHED';
  sequenceInRound: number;
  startedAt: string | null;
  /** The colour the club picked for the class as #rrggbb, or null for none. */
  classColour?: string | null;
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
  /** Points scored in the race's round, for a qualifying heat of a low-points class; absent or null otherwise. */
  points?: number | null;
  /** What the place in the event's finals earns in its championship, for a final of a scoring class; absent or null otherwise. */
  championshipPoints?: number | null;
  /** Still on the track when the race hit its maximum race window, so it did not take the flag. */
  dnf?: boolean;
};

export type PositionAtLap = {
  /** 0 is the grid (the starting place, no lap time); results stored before it existed have no lap 0. */
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
  /** The car's class colour as #rrggbb, for the overlay's class tags when a run mixes classes (#532); absent for none. */
  classColour?: string | null;
  driverName: string;
  carNumber: string | null;
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
  /** Start to the car's last crossing plus its time penalties; null before it has crossed. */
  totalTimeMs: number | null;
  /** The car has taken the flag: its time is up and its last lap has counted. */
  finished?: boolean;
  /** Time the referee has added so far, in milliseconds; the order, gaps and total already include it. */
  timePenaltyMs?: number;
  /** The car's 1-based place on the grid; absent or null when it had none. */
  startPosition?: number | null;
  /** The car's latest lap was a flying lap (beat the average of its earlier laps); shows a flame until its next lap. */
  flyingLap?: boolean;
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

export type MarshalPenaltyKind = 'QUALIFYING_SCORE' | 'FINALS_LAP';

export type MarshalPenaltyRequest = {
  entryId: number;
  eventId: number;
  kind: MarshalPenaltyKind;
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

/** Asks the streaming overlay to show a lower third for one car of the race (#533). */
export async function featureDriver(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/featured-driver`, { entryId });
}

/** Moves a pending final one place earlier or later in the event's run order (only when a final runs in legs). */
export async function moveFinal(raceId: number, direction: 'UP' | 'DOWN'): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/move-final`, { direction });
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

export async function applyMarshalPenalty(
  raceId: number,
  req: MarshalPenaltyRequest,
): Promise<void> {
  await api.post(`/api/v1/race-control/referee/race/${raceId}/apply-marshal-penalty`, req);
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

/** A car that crossed the line before its turn in a stagger start; `earlyMs` is how early its first crossing was. */
export type JumpStartDto = {
  entryId: number;
  driverName: string;
  carNumber: string | null;
  earlyMs: number;
  /** The referee took no action: the alert is gone but the entry keeps its lozenge. */
  dismissed: boolean;
};

export async function getJumpStarts(raceId: number): Promise<JumpStartDto[]> {
  const { data } = await api.get<JumpStartDto[]>(`/api/v1/race-control/race/${raceId}/jump-starts`);
  return data;
}

/** Takes no action on a car's jump start: its alert goes, its lozenge stays. */
export async function dismissJumpStart(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/referee/race/${raceId}/jump-start/${entryId}/dismiss`);
}

export async function moveEntryToHeat(
  raceId: number,
  req: { entryId: number; targetRaceId: number; allRounds: boolean },
): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/move-entry`, req);
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

/** A championship's name and the classes it scores, to head each class's table on the standings sheet. */
export type ChampionshipInfoDto = {
  id: number;
  name: string;
  classes: { racingClassId: number; name: string }[];
  /** The category tags the standings are also split by, in display order (#494) */
  splits?: string[];
};

export async function getPublicChampionshipInfo(championshipId: number): Promise<ChampionshipInfoDto> {
  const { data } = await api.get<ChampionshipInfoDto>(`/api/v1/championships/${championshipId}/info`);
  return data;
}

export type FinishedRaceDto = {
  id: number;
  roundLabel: string;
  className: string;
  heatNumber: number;
  isFinal: boolean;
};

export type EventScheduleDto = {
  id: number;
  name: string;
  eventDate: string;
  finishedRaces: FinishedRaceDto[];
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

export type PracticeRanking = 'FASTEST' | 'BEST_2' | 'BEST_3';

export async function getPracticeRanking(eventId: number): Promise<PracticeRanking> {
  const { data } = await api.get<{ ranking: PracticeRanking }>(`/api/v1/race-control/events/${eventId}/practice-ranking`);
  return data.ranking;
}

export async function setPracticeRanking(eventId: number, ranking: PracticeRanking): Promise<PracticeRanking> {
  const { data } = await api.put<{ ranking: PracticeRanking }>(
    `/api/v1/race-control/events/${eventId}/practice-ranking`,
    { ranking },
  );
  return data.ranking;
}

export type FinalLegsScope = 'A_ONLY' | 'ALL';

export async function getFinalLegsScope(eventId: number): Promise<FinalLegsScope> {
  const { data } = await api.get<{ scope: FinalLegsScope }>(`/api/v1/race-control/events/${eventId}/final-legs-scope`);
  return data.scope;
}

export async function setFinalLegsScope(eventId: number, scope: FinalLegsScope): Promise<FinalLegsScope> {
  const { data } = await api.put<{ scope: FinalLegsScope }>(
    `/api/v1/race-control/events/${eventId}/final-legs-scope`,
    { scope },
  );
  return data.scope;
}

// ── Auto run (#338) ──────────────────────────────────────────────────────────

export type AutoRunPhase = 'WAITING' | 'RACING' | 'HELD';
export type AutoRunHeldReason = 'FINALS' | 'NO_DRIVERS' | 'REFUSED';

export type AutoRunStateDto = {
  eventId: number;
  armed: boolean;
  phase: AutoRunPhase | null;
  nextRaceId: number | null;
  nextRaceLabel: string | null;
  /** When the next race starts, while waiting. */
  startAt: string | null;
  heldReason: AutoRunHeldReason | null;
  heldMessage: string | null;
  gapSeconds: number;
  holdBeforeFinals: boolean;
  /** Whether auto run switches itself on when a race is started by hand. */
  autoStart: boolean;
  /** The server's clock when this was built, for counting down without trusting this browser's clock. */
  serverTime?: string;
};

const autoRunUrl = (eventId: number) => `/api/v1/race-control/events/${eventId}/auto-run`;

export async function getAutoRun(eventId: number): Promise<AutoRunStateDto> {
  const { data } = await api.get<AutoRunStateDto>(autoRunUrl(eventId));
  return data;
}

export async function autoRunGo(eventId: number): Promise<AutoRunStateDto> {
  const { data } = await api.post<AutoRunStateDto>(`${autoRunUrl(eventId)}/go`);
  return data;
}

export async function autoRunStop(eventId: number): Promise<AutoRunStateDto> {
  const { data } = await api.post<AutoRunStateDto>(`${autoRunUrl(eventId)}/stop`);
  return data;
}

export async function autoRunSkipTo(eventId: number, seconds: number): Promise<AutoRunStateDto> {
  const { data } = await api.post<AutoRunStateDto>(`${autoRunUrl(eventId)}/skip-to`, { seconds });
  return data;
}

export async function setAutoRunSettings(
  eventId: number,
  settings: { gapSeconds: number; holdBeforeFinals: boolean; autoStart: boolean },
): Promise<AutoRunStateDto> {
  const { data } = await api.put<AutoRunStateDto>(`${autoRunUrl(eventId)}/settings`, settings);
  return data;
}

// ── Transponder swap (L11) ────────────────────────────────────────────────────

export type EntrySearchResult = {
  entryId: number;
  competitorName: string;
  competitorId: number | null;
  /** The override for how the name is said aloud; null means none. */
  spokenName: string | null;
  /** What the announcer says for this driver: the spoken name, else the name tidied (#120). */
  speechName: string;
  className: string | null;
  transponderNumber: string;
  secondaryTransponderNumber: string | null;
  /** The imported file's numbers where they differ from a transponder swapped on the day (#50) */
  importedTransponderNumber: string | null;
  importedSecondaryTransponderNumber: string | null;
  /** Optional third and fourth transponders (#482) */
  thirdTransponderNumber?: string | null;
  fourthTransponderNumber?: string | null;
};

export type TransponderSlot = 'PRIMARY' | 'SECONDARY' | 'THIRD' | 'FOURTH';

export type TransponderSwapResponse = {
  entryId: number;
  slot: TransponderSlot;
  oldTransponderNumber: string | null;
  newTransponderNumber: string | null;
};

/** Entries in the event whose competitor name contains the query. */
export async function searchEntries(eventId: number, query: string): Promise<EntrySearchResult[]> {
  const { data } = await api.get<EntrySearchResult[]>(
    `/api/v1/race-control/events/${eventId}/entries/search`,
    { params: { query } },
  );
  return data;
}

/** A blank number removes a secondary, third or fourth transponder. */
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
  | 'JUMP_START'
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

/** One finished race in the officials' results: the real-name result and each driver's grid position. */
export type EventResultsRace = {
  raceId: number;
  eventClassId: number;
  className: string;
  roundNumber: number;
  roundType: 'PRACTICE' | 'QUALIFIER' | 'FINAL';
  heatNumber: number;
  finalLetter: string | null;
  result: ResultSnapshotDto;
  /** Grid position by entry id; an entry with no grid position is missing. */
  startPositions: Record<string, number>;
  /** The scoring system the class's format was set from, so a printout can say how it was scored; null or missing when none. */
  scoringSystem?: string | null;
};

/** An event's finished races in run order, for the officials' printouts. Names are the real ones. */
export type EventResultsDto = {
  eventId: number;
  eventName: string;
  eventDate: string;
  races: EventResultsRace[];
};

/** Every finished race of the event, with real names and starting grid (round summary, finals, meeting pack). */
export async function getEventResults(eventId: number): Promise<EventResultsDto> {
  const { data } = await api.get<EventResultsDto>(`/api/v1/race-control/event/${eventId}/results`);
  return data;
}

/** One finished race in the same shape as {@link getEventResults}, for the heat result printout. */
export async function getRaceResult(raceId: number): Promise<EventResultsDto> {
  const { data } = await api.get<EventResultsDto>(`/api/v1/race-control/race/${raceId}/result`);
  return data;
}

/** A driver's place in one finished qualifying round; `counted` is false for a round that was dropped. */
export type QualifyingRoundPlace = {
  place: number;
  counted: boolean;
};

export type QualifyingTableRow = {
  position: number;
  entryId: number;
  driverName: string;
  carNumber: string | null;
  lapsCompleted: number;
  bestLapMs: number | null;
  points: number | null;
  /** The place in each finished round, in round order; empty unless the class ranks round by round. */
  rounds: QualifyingRoundPlace[];
  /** The heat, when the class's heats are separate overall races (positions count from 1 in each); else null. */
  heat?: number | null;
  /** What the place earns in the event's championship; set only for a class with no finals, once its heats are done. */
  championshipPoints?: number | null;
};

export type QualifyingTable = {
  eventClassId: number;
  className: string;
  heatsFinished: number;
  heatsTotal: number;
  finalsStarted: boolean;
  rows: QualifyingTableRow[];
};

export async function getQualifying(eventId: number): Promise<QualifyingTable[]> {
  const { data } = await api.get<QualifyingTable[]>(`/api/v1/race-control/event/${eventId}/qualifying`);
  return data;
}

/** The combined result of each class's A final that is run in legs; empty when no class has legs. */
export async function getFinalLegs(eventId: number): Promise<FinalLegs[]> {
  const { data } = await api.get<FinalLegs[]>(`/api/v1/race-control/event/${eventId}/final-legs`);
  return data;
}

/** Calculates a class's finals from its qualifying results; replaces any hand edits to the line-ups. */
export async function calculateFinals(
  eventId: number,
  eventClassId: number,
  carsPerFinal: number | null,
): Promise<void> {
  await api.post(`/api/v1/race-control/event/${eventId}/qualifying/${eventClassId}/seed-finals`, { carsPerFinal });
}

export type CalculateAllFinalsResult = { calculated: number[]; skipped: Record<string, string> };

/**
 * Calculates the finals of every class of the event that is ready; the others are left alone and named in `skipped`.
 * `carsPerFinal` gives the cars per final by event class id for the classes the official set a number for; a class
 * not listed uses its format's own grid size.
 */
export async function calculateAllFinals(
  eventId: number,
  carsPerFinal: Record<number, number> = {},
): Promise<CalculateAllFinalsResult> {
  const { data } = await api.post<CalculateAllFinalsResult>(
    `/api/v1/race-control/event/${eventId}/seed-all-finals`, { carsPerFinal });
  return data;
}

export type FinalsLineupDriverDto = {
  entryId: number;
  driverName: string;
  gridPosition: number | null;
  carNumber: number | null;
  qualifyingPosition: number | null;
};

export type FinalsLineupFinalDto = {
  raceId: number;
  letter: string;
  status: RunOrderItemDto['status'];
  /** Places at the back of the grid still to be filled by the winners of the final below. */
  bumpSlots: number;
  editable: boolean;
  drivers: FinalsLineupDriverDto[];
};

export type FinalsLineupDto = {
  eventClassId: number;
  className: string;
  qualifyingComplete: boolean;
  calculated: boolean;
  started: boolean;
  defaultCarsPerFinal: number;
  finals: FinalsLineupFinalDto[];
};

export async function getFinalsLineup(eventId: number): Promise<FinalsLineupDto[]> {
  const { data } = await api.get<FinalsLineupDto[]>(`/api/v1/race-control/event/${eventId}/finals-lineup`);
  return data;
}

export type FinalsReviewDto = {
  /** Qualifying is over, no final has been called and the line-ups are unconfirmed: the line-up screen replaces the cockpit. */
  required: boolean;
  confirmedAt: string | null;
};

export async function getFinalsReview(eventId: number): Promise<FinalsReviewDto> {
  const { data } = await api.get<FinalsReviewDto>(`/api/v1/race-control/event/${eventId}/finals-review`);
  return data;
}

/** Confirms the event's finals line-ups, which brings the cockpit back. */
export async function confirmFinalsLineup(eventId: number): Promise<FinalsReviewDto> {
  const { data } = await api.post<FinalsReviewDto>(`/api/v1/race-control/event/${eventId}/finals-lineup/confirm`);
  return data;
}

export async function moveFinalsDriver(
  raceId: number,
  req: { entryId: number; targetRaceId: number; targetPosition: number | null },
): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/finals/move`, req);
}

export async function swapFinalsDrivers(
  raceId: number,
  req: { entryId: number; otherRaceId: number; otherEntryId: number },
): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/finals/swap`, req);
}

export type RaceEliminationDto = {
  entryId: number;
  eliminatedAt: string;
  /** Null while the car is still dead; a row with a time is the record of a zombie revival. */
  revivedAt: string | null;
};

export type RaceEliminationsDto = {
  /** False for any race that is not run to the Shikabane format: race control hides the buttons. */
  shikabane: boolean;
  rows: RaceEliminationDto[];
};

export async function getEliminations(raceId: number): Promise<RaceEliminationsDto> {
  const { data } = await api.get<RaceEliminationsDto>(`/api/v1/race-control/race/${raceId}/eliminations`);
  return data;
}

export async function eliminateEntry(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/eliminations/eliminate`, { entryId });
}

export async function reviveEntry(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/eliminations/revive`, { entryId });
}

export async function undoElimination(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/eliminations/undo`, { entryId });
}

export async function falseStartEntry(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/eliminations/false-start`, { entryId });
}

export async function undoFalseStartEntry(raceId: number, entryId: number): Promise<void> {
  await api.post(`/api/v1/race-control/race/${raceId}/eliminations/false-start/undo`, { entryId });
}
