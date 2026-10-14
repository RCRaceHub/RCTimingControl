import { useCallback, useEffect, useRef, useState } from 'react';
import { useStomp } from '@/hooks/race-control/useStomp';
import type { AudioSettingsDto } from '@/lib/audioApi';
import { speakWithBrowser } from '@/lib/speech';

const DEFAULT_COUNTDOWN_INTERVALS = [600, 300, 120, 60, 30];

interface RunningOrderEvent {
  type: 'running-order';
  positions: string[];
}

export interface GridEntry {
  entryId: number;
  carNumber: string | null;
  driverName: string;
  /** What the announcer says for this driver (#119, #120); the driver name when the server sent none. */
  speechName?: string | null;
}

/** How long the grid call waits for the server's clips before using the browser voice. */
export const CLIP_GRACE_MS = 6000;

/** Clip key the server stores an entry's grid call under (AudioPreGenerationService). */
export function gridClipKey(entry: GridEntry): string {
  return `grid-${entry.entryId}`;
}

/** What the browser says for a grid call when no clip is available. */
export function gridFallbackText(entry: GridEntry): string {
  const name = entry.speechName?.trim() || entry.driverName;
  return entry.carNumber ? `Car ${entry.carNumber}, ${name}.` : `${name}.`;
}

interface UseAnnouncementsOptions {
  raceId: number | null;
  settings: AudioSettingsDto | null;
  volume?: number;
  raceState?: string | null;
  /**
   * When the race clock reaches zero, as epoch milliseconds, worked out from the server's race clock so that
   * stoppages are allowed for. Null when the race is not running or has no set length (the countdown is
   * silent then).
   */
  raceEndsAt?: number | null;
  gridEntries?: GridEntry[];
}

/**
 * Audio announcements hook (AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06, AUDIO-11).
 *
 * Subscribes to the race audio STOMP topic for running-order events and
 * provides utilities for Web Speech API fallback, beep sounds, clip playback,
 * countdown timer, stagger sequencer, and finish announcement.
 */
export function useAnnouncements({
  raceId,
  settings,
  volume = 0.8,
  raceState,
  raceEndsAt,
  gridEntries,
}: UseAnnouncementsOptions) {
  // Refs so closures inside timeouts always read latest values without stale captures
  const volumeRef = useRef(volume);
  useEffect(() => { volumeRef.current = volume; }, [volume]);

  const settingsRef = useRef(settings);
  useEffect(() => { settingsRef.current = settings; }, [settings]);

  const raceStateRef = useRef(raceState);
  useEffect(() => { raceStateRef.current = raceState; }, [raceState]);

  // Clip URL cache — populated externally via setClipMap (AUDIO-10)
  const clipMapRef = useRef<Record<string, string>>({});

  // Timeout collections for scheduled announcements
  const countdownTimeoutsRef = useRef<ReturnType<typeof setTimeout>[]>([]);
  const staggerTimeoutsRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  // Subscribe to race audio events (AUDIO-06 running-order trigger)
  const { data: audioEvent } = useStomp<RunningOrderEvent>(
    raceId ? `/topic/race/${raceId}/audio` : null,
  );

  // Web Speech API fallback (AUDIO-11)
  const fallbackSpeak = useCallback((text: string) => {
    speakWithBrowser(text, volumeRef.current);
  }, []);

  // playClip: play a pre-generated clip URL; fall back to Web Speech API (AUDIO-10, AUDIO-11)
  const playClip = useCallback((key: string, fallbackText: string) => {
    const url = clipMapRef.current[key];
    if (url) {
      const audio = new Audio(url);
      audio.volume = volumeRef.current;
      audio.play().catch(() => fallbackSpeak(fallbackText));
    } else {
      fallbackSpeak(fallbackText);
    }
  }, [fallbackSpeak]);

  // Which race the clip cache belongs to once it has clips; a different race has none yet
  const [clipsRaceId, setClipsRaceId] = useState<number | null>(null);
  const clipsReady = raceId != null && clipsRaceId === raceId;
  // setClipMap: populate the clip cache from outside (called by usePregeneratedClips)
  const setClipMap = useCallback((map: Record<string, string>) => {
    clipMapRef.current = map;
    setClipsRaceId(Object.keys(map).length > 0 ? raceId : null);
  }, [raceId]);

  // A different race has different clips: drop the previous race's URLs straight away
  useEffect(() => {
    clipMapRef.current = {};
  }, [raceId]);

  // Handle running-order announcements (AUDIO-06)
  useEffect(() => {
    if (!audioEvent) return;
    const s = settingsRef.current;
    if (!s?.announceRunningOrder) return;
    if (audioEvent.type === 'running-order' && audioEvent.positions?.length > 0) {
      const text = `Running order: ${audioEvent.positions
        .map((name, i) => `${i + 1}, ${name}`)
        .join('. ')}.`;
      fallbackSpeak(text);
    }
  }, [audioEvent, fallbackSpeak]);

  // Countdown timer: schedule announcements when race starts (AUDIO-02)
  useEffect(() => {
    countdownTimeoutsRef.current.forEach(clearTimeout);
    countdownTimeoutsRef.current = [];

    if (raceState !== 'RUNNING') return;
    const s = settingsRef.current;
    if (!s?.announceCountdown) return;
    if (!raceEndsAt) return;

    const intervals = s.countdownIntervals ?? DEFAULT_COUNTDOWN_INTERVALS;
    const now = Date.now();

    intervals.forEach((secsBeforeEnd) => {
      const delay = raceEndsAt - secsBeforeEnd * 1000 - now;
      if (delay <= 0) return;

      const mins = Math.floor(secsBeforeEnd / 60);
      const secs = secsBeforeEnd % 60;
      const label =
        mins > 0
          ? `${mins} minute${mins > 1 ? 's' : ''}${secs > 0 ? ` ${secs} seconds` : ''}`
          : `${secs} seconds`;
      const text = `${label} remaining.`;

      const t = setTimeout(() => {
        if (raceStateRef.current !== 'RUNNING') return;
        if (!settingsRef.current?.announceCountdown) return;
        playClip(`countdown-${secsBeforeEnd}`, text);
      }, delay);

      countdownTimeoutsRef.current.push(t);
    });

    return () => {
      countdownTimeoutsRef.current.forEach(clearTimeout);
      countdownTimeoutsRef.current = [];
    };
  }, [raceState, raceEndsAt, playClip]);

  // Finish announcement: fire when race transitions to FINISHED (AUDIO-05)
  useEffect(() => {
    if (raceState !== 'FINISHED') return;
    const s = settingsRef.current;
    if (!s?.announceFinish) return;
    playClip('finish', 'Race finished. Checkered flag.');
  }, [raceState, playClip]);

  // Guard so the stagger fires at most once per GRID entry, even if gridEntries refetches
  const staggerFiredRef = useRef(false);
  // The server makes the grid clips after GRID starts, so wait a moment for them
  const [graceRaceId, setGraceRaceId] = useState<number | null>(null);
  const graceElapsed = raceId != null && graceRaceId === raceId;
  useEffect(() => {
    if (raceState !== 'GRID') {
      staggerFiredRef.current = false;
      staggerTimeoutsRef.current.forEach(clearTimeout);
      staggerTimeoutsRef.current = [];
      return;
    }
    const t = setTimeout(() => setGraceRaceId(raceId), CLIP_GRACE_MS);
    return () => clearTimeout(t);
  }, [raceState, raceId]);

  // Stagger sequencer: call each driver in sequence at GRID state (AUDIO-03).
  // No cleanup here: a refetch of gridEntries must not cancel calls already scheduled.
  useEffect(() => {
    if (raceState !== 'GRID') return;
    if (staggerFiredRef.current) return;
    const s = settingsRef.current;
    if (!s?.announceStagger) return;
    if (!gridEntries || gridEntries.length === 0) return;
    if (!clipsReady && !graceElapsed) return;

    staggerFiredRef.current = true;
    staggerTimeoutsRef.current.forEach(clearTimeout);
    staggerTimeoutsRef.current = [];

    gridEntries.forEach((entry, index) => {
      const t = setTimeout(() => {
        if (raceStateRef.current !== 'GRID') return;
        if (!settingsRef.current?.announceStagger) return;
        playClip(gridClipKey(entry), gridFallbackText(entry));
      }, index * 2000);

      staggerTimeoutsRef.current.push(t);
    });
  }, [raceState, gridEntries, clipsReady, graceElapsed, playClip]);

  // Per-lap improvement beeps (AUDIO-04) via AudioContext
  const playBeep = useCallback(
    (improving: boolean) => {
      if (settingsRef.current && !settingsRef.current.announceLapBeep) return;
      try {
        const ctx = new AudioContext();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        gain.gain.value = volumeRef.current;
        osc.frequency.value = improving ? 880 : 440;
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.2);
        // Close the context after the beep to release its audio resources
        osc.onended = () => ctx.close();
      } catch (e) {
        console.warn('Beep failed:', e);
      }
    },
    [],
  );

  // Global cleanup on unmount
  useEffect(() => {
    return () => {
      countdownTimeoutsRef.current.forEach(clearTimeout);
      staggerTimeoutsRef.current.forEach(clearTimeout);
    };
  }, []);

  return {
    fallbackSpeak,
    playBeep,
    playClip,
    setClipMap,
  };
}
