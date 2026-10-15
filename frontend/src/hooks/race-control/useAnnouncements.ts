import { useCallback, useEffect, useRef } from 'react';
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
  /** How the race starts; the drivers are called one by one at the start only for STAGGER. */
  startType?: string | null;
  /** Seconds between driver calls in a stagger start (FORMAT-11). */
  staggerIntervalSeconds?: number;
}

/**
 * Audio announcements hook (AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06, AUDIO-11).
 *
 * Subscribes to the race audio STOMP topic for running-order events and
 * provides utilities for Web Speech API fallback, beep sounds, clip playback,
 * countdown timer, stagger sequencer (driver calls from the start of a stagger race), and finish announcement.
 */
export function useAnnouncements({
  raceId,
  settings,
  volume = 0.8,
  raceState,
  raceEndsAt,
  gridEntries,
  startType,
  staggerIntervalSeconds = 1,
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

  // setClipMap: populate the clip cache from outside (called by usePregeneratedClips)
  const setClipMap = useCallback((map: Record<string, string>) => {
    clipMapRef.current = map;
  }, []);

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

  // The stagger is called when the race starts (GRID to RUNNING), so each driver starts as their name is called
  // (AUDIO-03). It runs at most once per start, even if gridEntries refetches, and a race opened already
  // running does not replay it.
  const staggerArmedRef = useRef(false);
  const staggerFiredRef = useRef(false);
  const clearStagger = useCallback(() => {
    staggerArmedRef.current = false;
    staggerFiredRef.current = false;
    staggerTimeoutsRef.current.forEach(clearTimeout);
    staggerTimeoutsRef.current = [];
  }, []);

  // Cleanup only: a different race, or leaving the page, drops the calls and starts afresh
  useEffect(() => clearStagger, [raceId, clearStagger]);

  // No cleanup in this effect: a refetch of gridEntries must not cancel calls already scheduled.
  useEffect(() => {
    // Not yet known (the run order is reloading): leave things as they are
    if (!raceState) return;
    // Armed before the start, whether or not the grid was called first
    if (raceState === 'GRID' || raceState === 'PENDING') {
      staggerArmedRef.current = true;
      staggerFiredRef.current = false;
      return;
    }
    // A stop keeps the armed state: the race resumes without calling the drivers again
    if (raceState !== 'RUNNING') {
      if (raceState !== 'STOPPED') clearStagger();
      return;
    }
    if (!staggerArmedRef.current || staggerFiredRef.current) return;
    if (startType !== 'STAGGER') return;
    if (!settingsRef.current?.announceStagger) return;
    if (!gridEntries || gridEntries.length === 0) return;

    staggerFiredRef.current = true;
    const stepMs = Math.max(staggerIntervalSeconds, 1) * 1000;

    gridEntries.forEach((entry, index) => {
      const t = setTimeout(() => {
        if (raceStateRef.current !== 'RUNNING') return;
        if (!settingsRef.current?.announceStagger) return;
        playClip(gridClipKey(entry), gridFallbackText(entry));
      }, index * stepMs);

      staggerTimeoutsRef.current.push(t);
    });
  }, [raceState, gridEntries, startType, staggerIntervalSeconds, clearStagger, playClip]);

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
