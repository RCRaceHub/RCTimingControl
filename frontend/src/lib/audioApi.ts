import api from './api';
import type { HornSounds } from './sounds';

// ── Types ──────────────────────────────────────────────────────────────────

export interface VoiceInfo {
  voiceId: string;
  label: string;
  isDefault: boolean;
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
}

/** The announcement switches, in the order the settings screens show them. */
export const ANNOUNCEMENT_TOGGLES = [
  { key: 'announceCountdown', label: 'Countdown intervals' },
  { key: 'announceStagger', label: 'Stagger car calls' },
  { key: 'announceLapBeep', label: 'Lap improvement beeps' },
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

// ── Admin audio endpoints ──────────────────────────────────────────────────

/** GET admin-level audio settings (same DTO, different auth). */
export const getAdminAudioSettings = () =>
  api.get<AudioSettingsDto>('/api/v1/admin/audio/settings').then(r => r.data);

/** PUT admin-level audio settings. */
export const saveAdminAudioSettings = (settings: AudioSettingsDto) =>
  api.put<AudioSettingsDto>('/api/v1/admin/audio/settings', settings).then(r => r.data);

/** Replace the start or finish horn with the club's own sound file; `durationMs` is how long it plays. */
export const uploadHornSound = (horn: 'start' | 'finish', file: File, durationMs: number) => {
  const form = new FormData();
  form.append('file', file);
  form.append('durationMs', String(durationMs));
  return api.put<HornSounds>(`/api/v1/admin/audio/horns/${horn}`, form).then(r => r.data);
};

/** Go back to the built-in horn. */
export const removeHornSound = (horn: 'start' | 'finish') =>
  api.delete<HornSounds>(`/api/v1/admin/audio/horns/${horn}`).then(r => r.data);

// ── Piper test ─────────────────────────────────────────────────────────────

/** A spoken sample from the club's Piper voice, as a WAV; rejects if Piper is off or cannot be reached. */
export const getPiperTestClip = () =>
  api.get<Blob>('/api/v1/race-control/settings/audio/piper-test', { responseType: 'blob' }).then(r => r.data);
