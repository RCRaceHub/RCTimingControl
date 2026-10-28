import api from './api';
import type { HornSounds } from './sounds';

// ── Types ──────────────────────────────────────────────────────────────────

export interface VoiceInfo {
  voiceId: string;
  label: string;
  isDefault: boolean;
}

/** How each spoken line is worded; {seconds}, {race}, {drivers}, {marshals}, {positions}, {car} and {name} are filled in. */
export interface AudioWording {
  startLeadIn: string;
  gridCall: string;
  go: string;
  stop: string;
  resume: string;
  abandon: string;
  raceFinished: string;
  nextRace: string;
  marshals: string;
  runningOrder: string;
  carFinish: string;
}

/** The standard wording, which the server also sends for any line the club has left empty. */
export const DEFAULT_WORDING: AudioWording = {
  startLeadIn: 'Starting in {seconds} seconds.',
  gridCall: '{race}. {drivers} to the grid.',
  go: 'Race started.',
  stop: 'Race stopped.',
  resume: 'Race resumed.',
  abandon: 'Race abandoned.',
  raceFinished: 'Race finished. Checkered flag.',
  nextRace: 'Next race: {race}.',
  marshals: 'Marshals: {marshals}.',
  runningOrder: 'Running order: {positions}.',
  carFinish: 'Car {car}, {name}.',
};

/** Timings and tones; the server fills in any number left out. */
export interface AudioTuning {
  runningOrderEarlyPeriodMinutes: number;
  runningOrderEarlyEveryMinutes: number;
  runningOrderLaterEveryMinutes: number;
  /** How many of the last seconds get a count: the start lead-in's ticks and a timed race's final beeps. */
  finalSeconds: number;
  lapBeepHz: number;
  lapBeepMs: number;
  /** The grid call and each driver's call in a stagger start say first names only (#482). */
  firstNamesOnly: boolean;
}

export const DEFAULT_TUNING: AudioTuning = {
  runningOrderEarlyPeriodMinutes: 10,
  runningOrderEarlyEveryMinutes: 2,
  runningOrderLaterEveryMinutes: 5,
  finalSeconds: 5,
  lapBeepHz: 440,
  lapBeepMs: 200,
  firstNamesOnly: false,
};

/** The line with each {key} replaced by its value; a key with no value is left as it is. */
export function fillWording(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (whole, key: string) => (key in values ? String(values[key]) : whole));
}

/** A line of the club's wording, or the standard when the settings are not loaded or the line is empty. */
export function wordingOf(settings: AudioSettingsDto | null | undefined, key: keyof AudioWording): string {
  return settings?.wording?.[key]?.trim() || DEFAULT_WORDING[key];
}

/** The club's timings and tones, or the standard when the settings are not loaded. */
export function tuningOf(settings: AudioSettingsDto | null | undefined): AudioTuning {
  return { ...DEFAULT_TUNING, ...settings?.tuning };
}

export interface AudioSettingsDto {
  announceCountdown: boolean;
  announceStagger: boolean;
  announceLapBeep: boolean;
  announceFinish: boolean;
  announceRunningOrder: boolean;
  /** The share of the drivers each running-order call names, rounded up. */
  runningOrderPercent: number;
  /** A race with this many drivers or fewer has them all named. */
  runningOrderAllUpTo: number;
  defaultVoiceId: string;
  countdownIntervals?: number[];
  /** The server always sends these; a missing one counts as on (the lead-in as none). */
  startHorn?: boolean;
  finishHorn?: boolean;
  announceRaceCalls?: boolean;
  announceNextRace?: boolean;
  /** Seconds between pressing Start and the race starting; 0 for none. */
  startCountdownSeconds?: number;
  /** The club's own horn sound files, if it has uploaded any. */
  hornSounds?: HornSounds;
  /** Whether each car's number and name is called as it takes the flag. */
  announceCarFinish?: boolean;
  /** What is said when the race clock reaches zero; the server fills in the standard wording. */
  timeUpText?: string;
  /** Switches for the start lead-in's ticks and the beeps in the last seconds of a timed race. */
  tickSound?: boolean;
  finalBeeps?: boolean;
  /** Whether a lap beep tells the pace (higher when faster, lower when slower); off, every lap gets the plain beep. */
  paceBeeps?: boolean;
  wording?: AudioWording;
  tuning?: AudioTuning;
}

/** The announcement switches, in the order the settings screens show them. */
export const ANNOUNCEMENT_TOGGLES = [
  { key: 'announceCountdown', label: 'Countdown intervals' },
  { key: 'announceStagger', label: 'Stagger car calls' },
  { key: 'announceLapBeep', label: 'Lap beeps' },
  { key: 'paceBeeps', label: 'Faster/slower lap beeps (off: plain beep)' },
  { key: 'tickSound', label: 'Start lead-in ticks' },
  { key: 'finalBeeps', label: 'Final-seconds beeps at the end of a timed race' },
  { key: 'announceFinish', label: 'Finish announcements' },
  { key: 'announceRunningOrder', label: 'Running order' },
  { key: 'startHorn', label: 'Start horn' },
  { key: 'finishHorn', label: 'Finish horn' },
  { key: 'announceRaceCalls', label: 'Grid, start, stop and resume calls' },
  { key: 'announceNextRace', label: 'Next race and marshals' },
  { key: 'announceCarFinish', label: 'Car number and name as each car finishes' },
] as const satisfies readonly { key: keyof AudioSettingsDto; label: string }[];

// ── Public audio endpoints ─────────────────────────────────────────────────

/** List available TTS voices. */
export const listVoices = () =>
  api.get<VoiceInfo[]>('/api/v1/audio/voices').then(r => r.data);

/** Fetch the clip URL map for a race (used by race control for pre-generated clips). */
export const getRaceClipMap = (raceId: number) =>
  api.get<Record<string, string>>(`/api/v1/race/${raceId}/audio-clips`).then(r => r.data);

// ── Race-control audio settings ────────────────────────────────────────────

/** GET current audio settings for race control (announcement toggles, volume etc.). */
export const getAudioSettings = () =>
  api.get<AudioSettingsDto>('/api/v1/race-control/settings/audio').then(r => r.data);

/** PATCH audio settings for race control. */
export const patchAudioSettings = (settings: AudioSettingsDto) =>
  api.patch<AudioSettingsDto>('/api/v1/race-control/settings/audio', settings).then(r => r.data);

// ── Audio templates ────────────────────────────────────────────────────────

/** A named copy of the audio settings (never the club's own sound files) that race control can apply in one go. */
export interface AudioTemplateDto {
  id: number;
  name: string;
  settings: AudioSettingsDto;
}

const TEMPLATES_URL = '/api/v1/race-control/settings/audio/templates';

export const listAudioTemplates = () => api.get<AudioTemplateDto[]>(TEMPLATES_URL).then(r => r.data);

/** Save the settings in force now as a new template; rejects with 409 if the name is taken. */
export const createAudioTemplate = (name: string) =>
  api.post<AudioTemplateDto>(TEMPLATES_URL, { name }).then(r => r.data);

/** Replace a template's settings with the ones in force now. */
export const updateAudioTemplate = (id: number) =>
  api.put<AudioTemplateDto>(`${TEMPLATES_URL}/${id}`, {}).then(r => r.data);

/** Make a template the club's audio settings; the answer is the settings as saved. */
export const applyAudioTemplate = (id: number) =>
  api.post<AudioSettingsDto>(`${TEMPLATES_URL}/${id}/apply`).then(r => r.data);

export const deleteAudioTemplate = (id: number) => api.delete(`${TEMPLATES_URL}/${id}`).then(() => undefined);

// ── Admin audio endpoints ──────────────────────────────────────────────────

/** GET admin-level audio settings (same DTO, different auth). */
export const getAdminAudioSettings = () =>
  api.get<AudioSettingsDto>('/api/v1/admin/audio/settings').then(r => r.data);

/** PUT admin-level audio settings. */
export const saveAdminAudioSettings = (settings: AudioSettingsDto) =>
  api.put<AudioSettingsDto>('/api/v1/admin/audio/settings', settings).then(r => r.data);

/** Replace a horn, the tick or a lap beep with the club's own sound file; `durationMs` is how long it plays. */
export type SoundName = 'start' | 'finish' | 'tick' | 'lap-beep' | 'faster-beep' | 'slower-beep';

export const uploadHornSound = (horn: SoundName, file: File, durationMs: number) => {
  const form = new FormData();
  form.append('file', file);
  form.append('durationMs', String(durationMs));
  return api.put<HornSounds>(`/api/v1/admin/audio/horns/${horn}`, form).then(r => r.data);
};

/** Go back to the built-in horn. */
export const removeHornSound = (horn: SoundName) =>
  api.delete<HornSounds>(`/api/v1/admin/audio/horns/${horn}`).then(r => r.data);

// ── Piper test ─────────────────────────────────────────────────────────────

/** A spoken sample from the club's Piper voice, as a WAV; rejects if Piper is off or cannot be reached. */
export const getPiperTestClip = () =>
  api.get<Blob>('/api/v1/race-control/settings/audio/piper-test', { responseType: 'blob' }).then(r => r.data);
