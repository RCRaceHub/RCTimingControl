import api from './api';

// ── Types ──────────────────────────────────────────────────────────────────

export type EventStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'OPEN'
  | 'ENTRIES_CLOSED'
  | 'IN_PROGRESS'
  | 'COMPLETED';

export interface AdminEventListDto {
  id: number;
  name: string;
  eventDate: string; // ISO date e.g. "2026-06-15"
  status: EventStatus;
  trackName: string | null;
}

export type StartType = 'STAGGER' | 'GRID' | 'ROLLING';
export type QualifyingType = 'FTQ' | 'ROUND_BY_ROUND' | 'FASTEST_LAP' | 'CONSECUTIVE_LAPS';

export interface TimedRaceConfig {
  type: 'TIMED';
  durationMinutes: number;
  startType: StartType;
  qualifyingType: QualifyingType;
  racePaddingMinutes: number;
  staggerIntervalSeconds: number;
}

export interface BumpUpConfig {
  type: 'BUMP_UP';
  qualifyingHeats: number;
  heatDurationMinutes: number;
  bestHeatsCount: number;
  gridSize: number;
  bumpSpots: number;
  qualifyingStartType: StartType;
  finalsStartType: StartType;
  qualifyingType: QualifyingType;
  racePaddingMinutes: number;
  staggerIntervalSeconds: number;
}

export interface PointsFinalsConfig {
  type: 'POINTS_FINALS';
  qualifyingHeats: number;
  finalsCount: number;
  finalDurationMinutes: number;
  heatDurationMinutes: number;
  qualifyingStartType: StartType;
  finalsStartType: StartType;
  qualifyingType: QualifyingType;
  racePaddingMinutes: number;
  staggerIntervalSeconds: number;
}

export interface ShikabaneConfig {
  type: 'SHIKABANE';
  heatDurationMinutes: number;
  startType: StartType;
  staggerIntervalSeconds: number;
  falseStartPenaltyPoints: number;
  zombieBonusPoints: number;
}

export type RaceFormatConfig = TimedRaceConfig | BumpUpConfig | PointsFinalsConfig | ShikabaneConfig;

export interface EventClassDto {
  id: number;
  eventId: number | null;
  racingClassId: number | null;
  templateId: number | null;
  configSnapshot: RaceFormatConfig;
  configOverride: Record<string, unknown> | null;
  combinedRaceGroup: number | null;
}

export interface EventDetailDto {
  id: number;
  name: string;
  eventDate: string;
  status: EventStatus;
  trackId: number | null;
  classes: EventClassDto[];
  racehubLastImportAt: string | null;
  racehubLastRevision: number | null;
}

// RaceHub Entry Export v1 import (L7/L8)

export type RaceHubImportAction = 'CREATE' | 'UPDATE' | 'WITHDRAW' | 'UNCHANGED' | 'STALE' | 'SKIP';

export interface RaceHubImportResult {
  dryRun: boolean;
  blocked: boolean;
  applied: boolean;
  racehubEventName: string | null;
  revision: number | null;
  summary: {
    created: number;
    updated: number;
    withdrawn: number;
    unchanged: number;
    stale: number;
    skipped: number;
  };
  unmappedClasses: {
    racehubEventClassId: string;
    rcClassName: string | null;
    className: string | null;
    entryCount: number;
  }[];
  errors: string[];
  warnings: string[];
  rows: {
    entryId: string;
    entryVersion: number;
    driverDisplayName: string | null;
    action: RaceHubImportAction;
    eventClassId: number | null;
    rctcEntryId: number | null;
  }[];
}

// RC-Timing CSV import (#39, #40)

export type CsvImportGroup = 'NEW' | 'CHANGED' | 'UNCHANGED' | 'MISSING' | 'SKIPPED';

export interface CsvImportRow {
  group: CsvImportGroup;
  /** Identifies a file row; changed rows are picked for update by it */
  key: string | null;
  /** Line in the file; null for a missing entry */
  line: number | null;
  name: string | null;
  brcaNumber: number | null;
  className: string | null;
  classNumber: number | null;
  eventClassId: number | null;
  /** The entry in RCTC; missing entries are picked for withdrawal by it */
  entryId: number | null;
  primaryTransponder: string | null;
  secondaryTransponder: string | null;
  changes: { field: string; before: string | null; after: string | null }[];
  /** RC-Timing columns RCTC doesn't keep, such as Grade and Car Make */
  info: Record<string, string>;
  applied: boolean;
  reason: string | null;
}

export interface CsvImportResult {
  dryRun: boolean;
  blocked: boolean;
  applied: boolean;
  summary: {
    newEntries: number;
    changed: number;
    unchanged: number;
    missing: number;
    skipped: number;
    created: number;
    updated: number;
    withdrawn: number;
  };
  unmappedClasses: { key: string; className: string | null; classNumber: number | null; entryCount: number }[];
  errors: string[];
  warnings: string[];
  rows: CsvImportRow[];
}

// Entry feed: an event's entries pulled from a URL (#42)
export type EntryFeedStatus = 'APPLIED' | 'UNCHANGED' | 'WAITING' | 'AUTH_FAILED' | 'FAILED';

export interface EntryFeedDto {
  url: string;
  /** The token itself is never sent back */
  tokenSaved: boolean;
  /** The saved token's last four characters, when it is long enough to show them */
  tokenHint: string | null;
  autoFetch: boolean;
  lastFetchAt: string | null;
  lastStatus: EntryFeedStatus | null;
  lastMessage: string | null;
  appliedRevision: number | null;
  /** A fetched file waits for an official to preview and confirm it */
  waiting: boolean;
  waitingRevision: number | null;
}

export interface SaveEntryFeedRequest {
  url: string;
  /** Leave out to keep the saved token; an empty string removes it */
  token?: string;
  autoFetch: boolean;
}

export interface RaceHubClassMappingDto {
  racehubEventClassId: string;
  eventClassId: number;
}

/** How many changes to ask for at a time; a shorter page than this is the last one. */
export const OFFICIAL_CHANGES_PAGE_SIZE = 50;

export interface EntryHistoryItem {
  at: string;
  actor: string | null;
  summary: string;
  reason: string | null;
}

export interface AdminEntryDto {
  id: number;
  competitorId: number | null;
  displayName: string | null;
  transponderNumber: string | null;
  secondaryTransponderNumber: string | null;
  /** The imported file's numbers where they differ from a transponder swapped on the day (#50) */
  importedTransponderNumber: string | null;
  importedSecondaryTransponderNumber: string | null;
  status: 'PENDING' | 'CONFIRMED' | 'WITHDRAWN';
  submittedAt: string;
  withdrawnAt: string | null;
}

export interface CreateEventRequest {
  name: string;
  eventDate: string;
  trackId: number | null;
}

export interface UpdateEventRequest {
  name: string;
  eventDate: string;
  trackId: number | null;
}

export interface TransitionEventRequest {
  targetStatus: EventStatus;
}

export interface ClassFinalsConfigDto {
  eventClassId: number;
  finalsCount: number | null;
}

export interface GenerateRoundsRequest {
  practiceRoundsCount: number;
  qualifyingRoundsCount: number;
  maxCarsPerHeat: number;
  minCarsPerHeat: number;
  classFinalsConfigs: ClassFinalsConfigDto[];
}

export interface AddEventClassRequest {
  racingClassId: number;
  templateId: number;
}

export interface UpdateEventClassOverrideRequest {
  override: Record<string, unknown>;
}

export interface CombineClassesRequest {
  eventClassIds: number[];
}

export interface WithdrawEntryRequest {
  reason: string;
}

export interface RacingClassDto {
  id: number;
  name: string;
  description: string | null;
}

export interface RaceFormatTemplateDto {
  id: number;
  name: string;
  config: RaceFormatConfig;
}

export interface TrackLapThresholdDto {
  id: number;
  /** Null for the track's default, which applies to every class without its own. */
  racingClassId: number | null;
  racingClassName: string | null;
  minLapMs: number;
  maxLastLapMs: number | null;
}

export interface TrackDto {
  id: number;
  name: string;
  venueNotes: string | null;
  trackLength: number | null;
  lapThresholds?: TrackLapThresholdDto[];
}

/** What is sent to create or change a track; its lap limits are saved separately. */
export type TrackInput = Pick<TrackDto, 'name' | 'venueNotes' | 'trackLength'>;

/** The track-wide lap limits in seconds, or null for none. The longest wait needs a shortest lap as well. */
export interface DefaultLapLimits {
  minLapSeconds: number | null;
  maxLastLapSeconds: number | null;
}

export type ScoringSource = 'QUALIFYING' | 'FINALS' | 'BOTH';

export interface ChampionshipDto {
  id: number;
  name: string;
  bestXFromYX: number | null;
  bestXFromYY: number | null;
  scoringSource: ScoringSource;
  tqBonusPoints: number;
  afinalWinnerBonusPoints: number;
}

export interface ChampionshipClassDto {
  id: number;
  championshipId: number;
  racingClassId: number;
  bestXFromYX: number | null;
  bestXFromYY: number | null;
}

export interface ChampionshipEventLinkDto {
  id: number;
  championshipId: number;
  eventId: number;
  roundNumber: number;
}

export interface PointsScaleEntryDto {
  position: number;
  points: number;
}

export interface ChampionshipExclusionDto {
  id: number;
  championshipId: number;
  /** Competitor id. */
  driverId: number;
  eventId: number;
  reason: string;
  /** User id of the official who recorded it. */
  createdBy: number;
  /** That official's name; null if the account has been removed. */
  createdByName: string | null;
  createdAt: string;
}

export interface ChampionshipDetailDto extends ChampionshipDto {
  classes: ChampionshipClassDto[];
  events: ChampionshipEventLinkDto[];
  pointsScale: PointsScaleEntryDto[];
}

export interface RoundResultDto {
  roundNumber: number;
  eventId: number;
  eventName: string;
  position: number;
  points: number;
  excluded: boolean;
  dropped: boolean;
}

export interface StandingsRowDto {
  /** Competitor id; exclusions use the same id. */
  driverId: number;
  displayName: string;
  racingClassId: number;
  totalPoints: number;
  rounds: RoundResultDto[];
}

export interface CreateWalkInEntryRequest {
  eventId: number;
  eventClassId: number;
  /** An existing competitor, or leave out and give competitorName for a new one. */
  competitorId?: number;
  competitorName?: string;
  /**
   * Say that a typed name is a different person from an existing driver of the same name. Without
   * it, such a name is refused with a 409 listing the matches (#123).
   */
  confirmNewCompetitor?: boolean;
  primaryTransponder: string;
  secondaryTransponder?: string;
}

export interface CreateWalkInEntryResult {
  entry: { id: number; status: string; transponderNumberSnapshot: string };
  warnings: string[];
}

export interface CompetitorSummaryDto {
  id: number;
  displayName: string;
  brcaNumber: string | null;
  homeClub: string | null;
  /** The admin's override for how the name is said aloud; null means none (#119). */
  spokenName: string | null;
  /** What the announcer says: the spoken name, else the display name tidied for speech (#120). */
  speechName: string;
  /** How the name is shown on public screens and in the live feed (#265). */
  nameVisibility: NameVisibility;
}

/** One recorded change to a competitor: when, who, and the spoken name before and after (null means none). */
export interface CompetitorChangeDto {
  at: string;
  by: string;
  action: string;
  before: string | null;
  after: string | null;
}

/** Competitors that may be one person entered twice (#123). */
export interface CompetitorDuplicateGroupDto {
  reason: string;
  competitors: CompetitorSummaryDto[];
}

/** SHOW the full name, INITIALS only, or HIDE it (#265). */
export type NameVisibility = 'SHOW' | 'INITIALS' | 'HIDE';

export interface CompetitorMergeSide {
  id: number;
  displayName: string;
  brcaNumber: string | null;
  homeClub: string | null;
  spokenName: string | null;
  externalSource: string | null;
  entries: number;
}

/** What merging a duplicate into the competitor to keep would do (#123). */
export interface CompetitorMergePreview {
  keep: CompetitorMergeSide;
  duplicate: CompetitorMergeSide;
  entriesToMove: number;
  eventsAffected: number;
  exclusionsToMove: number;
  resultingSpokenName: string | null;
  resultingExternalSource: string | null;
  warnings: string[];
  /** Reasons the merge can't go ahead. */
  blockers: string[];
  canMerge: boolean;
}

export interface CompetitorMergeResult {
  keptCompetitorId: number;
  entriesMoved: number;
  exclusionsMoved: number;
}

/** Counts of what anonymising a competitor changed (#263). */
export interface CompetitorAnonymiseResult {
  competitorId: number;
  entries: number;
  resultSnapshots: number;
  exportPayloads: number;
  competitorHistoryRows: number;
  entryHistoryRows: number;
  auditRows: number;
}

export interface ClubProfileDto {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  websiteUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  timezone: string;
  logoUrl: string | null;
}

export interface UpdateClubProfileRequest {
  name: string;
  email: string | null;
  phone: string | null;
  websiteUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  timezone: string;
}

export interface BackupFileDto {
  name: string;
  sizeBytes: number;
  createdAt: string;
  /** manual, nightly or day-close */
  reason: string;
}

export interface BackupsDto {
  /** The folder backups are written to, as the server sees it */
  directory: string;
  backups: BackupFileDto[];
}

// Officials (#61)
export type OfficialRole = 'ADMIN' | 'RACE_DIRECTOR' | 'REFEREE';

export interface OfficialDto {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: OfficialRole[];
  enabled: boolean;
  disabledAt: string | null;
  createdAt: string;
}

export type OfficialAction = 'ADDED' | 'ROLES_CHANGED' | 'PASSWORD_SET' | 'DISABLED' | 'ENABLED';

export interface OfficialChangeDto {
  id: number;
  at: string;
  officialId: number;
  officialName: string;
  action: OfficialAction;
  detail: string | null;
  actorId: number | null;
  /** Null when the change came from the laptop's command line */
  actorName: string | null;
}

export interface AddOfficialRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  roles: OfficialRole[];
}

// Results sent to RaceHub (#27)
export type ResultsExportStatus = 'QUEUED' | 'FAILED' | 'SENT' | 'SUPERSEDED';
/** Where the club publishes results (#319); SITE is the results site, not built yet */
export type ResultsPublishTarget = 'NONE' | 'RACEHUB' | 'SITE' | 'BOTH';
export type ResultsExportReason = 'RACE_FINISHED' | 'CORRECTION' | 'DAY_CLOSE';

export interface ResultsExportRowDto {
  id: number;
  eventId: number;
  eventName: string;
  revision: number;
  reason: ResultsExportReason;
  status: ResultsExportStatus;
  attempts: number;
  nextAttemptAt: string;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface ResultsExportsDto {
  /** Whether the RaceHub address and key are both set; without them, exports wait in the queue */
  sendingEnabled: boolean;
  /** The address in use: the one set here, else the settings file's */
  resultsUrl: string | null;
  /** The address set here, or null when the settings file's is in use */
  savedUrl: string | null;
  /** What is still needed before anything is sent */
  missingSettings: string[];
  /** Where results are published now: the club's choice, else the default */
  publishTarget: ResultsPublishTarget;
  /** The club's choice, or null when it has not made one */
  chosenTarget: ResultsPublishTarget | null;
  /** Newest first */
  exports: ResultsExportRowDto[];
}

// ── API client ─────────────────────────────────────────────────────────────

/** {@code customised}: whether {@code text} is the club's own, or the default */
export interface PrivacyNoticeEditDto {
  text: string;
  customised: boolean;
}

export const adminApi = {
  // RaceHub import. A blocked import answers 422 with the same preview body, so return it.
  importRaceHubEntries: (eventId: number, exportDocument: unknown, dryRun: boolean) =>
    api
      .post<RaceHubImportResult>(
        `/api/v1/admin/events/${eventId}/racehub-import`,
        exportDocument,
        { params: { dryRun }, validateStatus: s => (s >= 200 && s < 300) || s === 422 },
      )
      .then(r => r.data),

  // RC-Timing CSV import. Without dryRun, creates the new rows and applies only the picked updates
  // and withdrawals. A blocked import answers 422 with the same preview body, so return it.
  importCsvEntries: (eventId: number, file: File, dryRun: boolean, update: string[] = [], withdraw: number[] = []) => {
    const form = new FormData();
    form.append('file', file);
    update.forEach(key => form.append('update', key));
    withdraw.forEach(id => form.append('withdraw', String(id)));
    return api
      .post<CsvImportResult>(`/api/v1/admin/events/${eventId}/csv-import`, form, {
        params: { dryRun },
        validateStatus: s => (s >= 200 && s < 300) || s === 422,
      })
      .then(r => r.data);
  },

  // Entry feed (#42). No feed answers 204, returned as null.
  getEntryFeed: (eventId: number) =>
    api
      .get<EntryFeedDto | ''>(`/api/v1/admin/events/${eventId}/entry-feed`)
      .then(r => (r.status === 204 || !r.data ? null : r.data)),

  saveEntryFeed: (eventId: number, request: SaveEntryFeedRequest) =>
    api.put<EntryFeedDto>(`/api/v1/admin/events/${eventId}/entry-feed`, request).then(r => r.data),

  deleteEntryFeed: (eventId: number) =>
    api.delete(`/api/v1/admin/events/${eventId}/entry-feed`).then(() => undefined),

  /** Fetches now. Failures come back in the feed's status rather than as an error. */
  fetchEntryFeed: (eventId: number) =>
    api.post<EntryFeedDto>(`/api/v1/admin/events/${eventId}/entry-feed/fetch`).then(r => r.data),

  previewEntryFeed: (eventId: number) =>
    api.post<RaceHubImportResult>(`/api/v1/admin/events/${eventId}/entry-feed/preview`).then(r => r.data),

  // A blocked import answers 422 with the same preview body, so return it.
  applyEntryFeed: (eventId: number) =>
    api
      .post<RaceHubImportResult>(`/api/v1/admin/events/${eventId}/entry-feed/apply`, undefined, {
        validateStatus: s => (s >= 200 && s < 300) || s === 422,
      })
      .then(r => r.data),

  listRaceHubClassMappings: (eventId: number) =>
    api
      .get<RaceHubClassMappingDto[]>(`/api/v1/admin/events/${eventId}/racehub-class-mappings`)
      .then(r => r.data),

  replaceRaceHubClassMappings: (eventId: number, mappings: RaceHubClassMappingDto[]) =>
    api
      .put<RaceHubClassMappingDto[]>(`/api/v1/admin/events/${eventId}/racehub-class-mappings`, mappings)
      .then(r => r.data),

  // Events
  listEvents: () =>
    api.get<AdminEventListDto[]>('/api/v1/admin/events').then(r => r.data),

  getEvent: (id: number) =>
    api.get<EventDetailDto>(`/api/v1/admin/events/${id}`).then(r => r.data),

  createEvent: (body: CreateEventRequest) =>
    api.post<EventDetailDto>('/api/v1/admin/events', body).then(r => r.data),

  updateEvent: (id: number, body: UpdateEventRequest) =>
    api.put<EventDetailDto>(`/api/v1/admin/events/${id}`, body).then(r => r.data),

  transitionEvent: (id: number, targetStatus: EventStatus) =>
    api
      .post<EventDetailDto>(`/api/v1/admin/events/${id}/transition`, { targetStatus })
      .then(r => r.data),

  generateRounds: (id: number, body: GenerateRoundsRequest) =>
    api
      .post<void>(`/api/v1/admin/events/${id}/generate-rounds`, body)
      .then(r => r.data),

  regenerateRounds: (id: number, body: GenerateRoundsRequest) =>
    api
      .post<void>(`/api/v1/admin/events/${id}/regenerate-rounds`, body)
      .then(r => r.data),

  // Event classes

  addEventClass: (eventId: number, body: AddEventClassRequest) =>
    api
      .post<EventClassDto>(`/api/v1/admin/events/${eventId}/classes`, body)
      .then(r => r.data),

  updateOverrides: (eventId: number, classId: number, override: Record<string, unknown>) =>
    api
      .put<EventClassDto>(
        `/api/v1/admin/events/${eventId}/classes/${classId}/overrides`,
        { override }
      )
      .then(r => r.data),

  combineClasses: (eventId: number, eventClassIds: number[]) =>
    api
      .post<EventClassDto[]>(`/api/v1/admin/events/${eventId}/classes/combine`, {
        eventClassIds,
      })
      .then(r => r.data),

  // Racing classes
  listRacingClasses: () =>
    api.get<RacingClassDto[]>('/api/v1/admin/classes').then(r => r.data),

  // Entries
  listEntriesForClass: (eventId: number, classId: number) =>
    api
      .get<AdminEntryDto[]>(
        `/api/v1/admin/entries/events/${eventId}/classes/${classId}`
      )
      .then(r => r.data),

  createWalkInEntry: (body: CreateWalkInEntryRequest) =>
    api.post<CreateWalkInEntryResult>('/api/v1/admin/entries', body).then(r => r.data),

  /** What has happened to one entry, oldest first (admins only). */
  entryHistory: (entryId: number) =>
    api.get<EntryHistoryItem[]>(`/api/v1/admin/entries/${entryId}/history`).then(r => r.data),

  withdrawEntry: (entryId: number, reason: string) =>
    api
      .post(`/api/v1/admin/entries/${entryId}/withdraw`, { reason })
      .then(r => r.data),

  // Championships
  championships: {
    list: () =>
      api.get<ChampionshipDto[]>('/api/v1/admin/championships').then(r => r.data),
    get: (id: number) =>
      api.get<ChampionshipDetailDto>(`/api/v1/admin/championships/${id}`).then(r => r.data),
    create: (body: Omit<ChampionshipDto, 'id'>) =>
      api.post<ChampionshipDto>('/api/v1/admin/championships', body).then(r => r.data),
    update: (id: number, body: Omit<ChampionshipDto, 'id'>) =>
      api.put<ChampionshipDto>(`/api/v1/admin/championships/${id}`, body).then(r => r.data),
    addClass: (id: number, body: { racingClassId: number; bestXFromYX: number | null; bestXFromYY: number | null }) =>
      api.post<ChampionshipClassDto>(`/api/v1/admin/championships/${id}/classes`, body).then(r => r.data),
    removeClass: (id: number, racingClassId: number) =>
      api.delete(`/api/v1/admin/championships/${id}/classes/${racingClassId}`),
    linkEvent: (id: number, body: { eventId: number; roundNumber: number }) =>
      api.post<ChampionshipEventLinkDto>(`/api/v1/admin/championships/${id}/events`, body).then(r => r.data),
    unlinkEvent: (id: number, eventId: number) =>
      api.delete(`/api/v1/admin/championships/${id}/events/${eventId}`),
    replacePointsScale: (id: number, entries: PointsScaleEntryDto[]) =>
      api.put<PointsScaleEntryDto[]>(`/api/v1/admin/championships/${id}/points-scale`, { entries }).then(r => r.data),
    listExclusions: (id: number) =>
      api.get<ChampionshipExclusionDto[]>(`/api/v1/admin/championships/${id}/exclusions`).then(r => r.data),
    createExclusion: (id: number, body: { driverId: number; eventId: number; reason: string }) =>
      api.post<ChampionshipExclusionDto>(`/api/v1/admin/championships/${id}/exclusions`, body).then(r => r.data),
    deleteExclusion: (id: number, exclusionId: number) =>
      api.delete(`/api/v1/admin/championships/${id}/exclusions/${exclusionId}`),
    getStandings: (id: number) =>
      api.get<StandingsRowDto[]>(`/api/v1/admin/championships/${id}/standings`).then(r => r.data),
  },

  // Club profile
  club: {
    getProfile: () =>
      api.get<ClubProfileDto>('/api/v1/admin/club/profile').then(r => r.data),
    updateProfile: (body: UpdateClubProfileRequest) =>
      api.put<ClubProfileDto>('/api/v1/admin/club/profile', body).then(r => r.data),
    getPrivacyNotice: () =>
      api.get<PrivacyNoticeEditDto>('/api/v1/admin/club/privacy-notice').then(r => r.data),
    /** A blank text goes back to the default notice */
    setPrivacyNotice: (text: string) =>
      api.put<PrivacyNoticeEditDto>('/api/v1/admin/club/privacy-notice', { text }).then(r => r.data),
    uploadLogo: (file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      return api.put<{ logoUrl: string }>('/api/v1/admin/club/logo', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then(r => r.data);
    },
  },

  // Tracks (full CRUD)
  tracks: {
    list: () =>
      api.get<TrackDto[]>('/api/v1/admin/tracks').then(r => r.data),
    get: (id: number) =>
      api.get<TrackDto>(`/api/v1/admin/tracks/${id}`).then(r => r.data),
    create: (body: TrackInput) =>
      api.post<TrackDto>('/api/v1/admin/tracks', body).then(r => r.data),
    update: (id: number, body: TrackInput) =>
      api.put<TrackDto>(`/api/v1/admin/tracks/${id}`, body).then(r => r.data),
    /**
     * Sets the track's default lap limits (the ones for every class without its own), or takes them away when
     * no shortest lap is given. {@code existingId} is the default's id if the track already has one.
     */
    saveDefaultLimits: async (trackId: number, limits: DefaultLapLimits, existingId: number | undefined) => {
      if (limits.minLapSeconds === null) {
        if (existingId !== undefined) {
          await api.delete(`/api/v1/admin/tracks/thresholds/${existingId}`);
        }
        return;
      }
      await api.post(`/api/v1/admin/tracks/${trackId}/thresholds`, {
        racingClassId: null,
        minLapMs: Math.round(limits.minLapSeconds * 1000),
        maxLastLapMs: limits.maxLastLapSeconds === null ? null : Math.round(limits.maxLastLapSeconds * 1000),
      });
    },
    delete: (id: number) =>
      api.delete(`/api/v1/admin/tracks/${id}`),
  },

  // Format templates (full CRUD)
  formats: {
    list: () =>
      api.get<RaceFormatTemplateDto[]>('/api/v1/admin/formats').then(r => r.data),
    get: (id: number) =>
      api.get<RaceFormatTemplateDto>(`/api/v1/admin/formats/${id}`).then(r => r.data),
    create: (body: { name: string; config: RaceFormatConfig }) =>
      api.post<RaceFormatTemplateDto>('/api/v1/admin/formats', body).then(r => r.data),
    update: (id: number, body: { name: string; config: RaceFormatConfig }) =>
      api.put<RaceFormatTemplateDto>(`/api/v1/admin/formats/${id}`, body).then(r => r.data),
    delete: (id: number) =>
      api.delete(`/api/v1/admin/formats/${id}`),
  },

  // Competitors, also used for the driver search in championship exclusions
  competitors: {
    list: () =>
      api.get<CompetitorSummaryDto[]>('/api/v1/admin/competitors').then(r => r.data),
    /** Set, change or clear (empty text) how a name is said aloud (#119). */
    setSpokenName: (id: number, spokenName: string) =>
      api.put<CompetitorSummaryDto>(`/api/v1/admin/competitors/${id}/spoken-name`, { spokenName })
        .then(r => r.data),
    /** Who changed how a competitor's name is said, newest first. Admin only. */
    changes: (id: number) =>
      api.get<CompetitorChangeDto[]>(`/api/v1/admin/competitors/${id}/changes`).then(r => r.data),
    /** Sets how the name is shown on public screens and in the live feed (#265). */
    setNameVisibility: (id: number, nameVisibility: NameVisibility) =>
      api.put<CompetitorSummaryDto>(`/api/v1/admin/competitors/${id}/name-visibility`, { nameVisibility })
        .then(r => r.data),
    possibleDuplicates: () =>
      api.get<CompetitorDuplicateGroupDto[]>('/api/v1/admin/competitors/possible-duplicates').then(r => r.data),
    mergePreview: (keepId: number, duplicateId: number) =>
      api.get<CompetitorMergePreview>('/api/v1/admin/competitors/merge-preview', { params: { keepId, duplicateId } })
        .then(r => r.data),
    /** Moves the duplicate's entries onto the competitor to keep and deletes the duplicate. */
    merge: (keepId: number, duplicateId: number) =>
      api.post<CompetitorMergeResult>('/api/v1/admin/competitors/merge', { keepId, duplicateId }).then(r => r.data),
    /** Everything held about one competitor, as a JSON file to save. Admin only; audited (#263). */
    exportPersonalData: (id: number) =>
      api.get<Blob>(`/api/v1/admin/competitors/${id}/personal-data`, { responseType: 'blob' })
        .then(r => ({ blob: r.data, filename: `competitor-${id}.json` })),
    /** Removes the name and identifiers, keeping results and standings. Cannot be undone (#263). */
    anonymise: (id: number) =>
      api.post<CompetitorAnonymiseResult>(`/api/v1/admin/competitors/${id}/anonymise`).then(r => r.data),
    /** The text spoken in the club's Piper voice, as audio. Rejects with a 503 when Piper is down. */
    previewSpeech: (text: string) =>
      api.post<Blob>('/api/v1/admin/competitors/spoken-name/preview', { text }, { responseType: 'blob' })
        .then(r => r.data),
  },

  // Database backups (#22)
  backups: {
    list: () =>
      api.get<BackupsDto>('/api/v1/admin/backups').then(r => r.data),
    create: () =>
      api.post<BackupFileDto>('/api/v1/admin/backups').then(r => r.data),
  },

  // Officials (#61)
  officials: {
    list: () =>
      api.get<OfficialDto[]>('/api/v1/admin/officials').then(r => r.data),
    /** Newest first, one page at a time; `before` is the id of the oldest change already shown. */
    changes: (before?: number) =>
      api.get<OfficialChangeDto[]>('/api/v1/admin/officials/changes',
        { params: { before, size: OFFICIAL_CHANGES_PAGE_SIZE } })
        .then(r => r.data),
    add: (body: AddOfficialRequest) =>
      api.post<OfficialDto>('/api/v1/admin/officials', body).then(r => r.data),
    changeRoles: (id: number, roles: OfficialRole[]) =>
      api.put<OfficialDto>(`/api/v1/admin/officials/${id}/roles`, { roles }).then(r => r.data),
    setPassword: (id: number, password: string) =>
      api.put(`/api/v1/admin/officials/${id}/password`, { password }).then(() => undefined),
    disable: (id: number) =>
      api.post<OfficialDto>(`/api/v1/admin/officials/${id}/disable`).then(r => r.data),
    enable: (id: number) =>
      api.post<OfficialDto>(`/api/v1/admin/officials/${id}/enable`).then(r => r.data),
  },

  // Results sent to RaceHub (#27)
  resultsExports: {
    list: () =>
      api.get<ResultsExportsDto>('/api/v1/admin/results-exports').then(r => r.data),
    retry: (id: number) =>
      api.post(`/api/v1/admin/results-exports/${id}/retry`).then(() => undefined),
    /** Blank clears it, so the settings file's address applies */
    setAddress: (resultsUrl: string) =>
      api.put<ResultsExportsDto>('/api/v1/admin/results-exports/address', { resultsUrl }).then(r => r.data),
    /** Null clears the choice, so the default applies */
    setTarget: (target: ResultsPublishTarget | null) =>
      api.put<ResultsExportsDto>('/api/v1/admin/results-exports/target', { target }).then(r => r.data),
    /** The event's results as they stand now, as a Results Export v1 file */
    download: (eventId: number) =>
      api
        .get<Blob>(`/api/v1/admin/events/${eventId}/results-export`, { responseType: 'blob' })
        .then(r => ({ blob: r.data, filename: filenameFrom(r.headers['content-disposition']) ?? `results-event-${eventId}.json` })),
  },
};

function filenameFrom(contentDisposition: unknown): string | null {
  if (typeof contentDisposition !== 'string') return null;
  const match = /filename="?([^";]+)"?/.exec(contentDisposition);
  return match ? match[1] : null;
}
