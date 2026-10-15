import { useCallback, useEffect, useRef } from 'react';
import { useStomp } from '@/hooks/race-control/useStomp';
import type { AudioSettingsDto } from '@/lib/audioApi';
import { speakWithBrowser } from '@/lib/speech';
import { FINISH_HORN_MS, START_HORN_MS, playFinishHorn, playStartHorn } from '@/lib/sounds';

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

/** The race after a finished one, for the call between races. */
export interface NextRaceCall {
  label: string;
  /** The drivers who marshal it, from the race that has just finished. */
  marshals: string[];
}

/** How long after the finish line the next race is called. */
const NEXT_RACE_DELAY_MS = 4000;

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
  /** The selected race's name and how many drivers are on its grid, for the grid call. */
  raceLabel?: string;
  /** The race after this one, once this one has finished. */
  nextRace?: NextRaceCall | null;
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
  raceLabel,
  nextRace,
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

  // What the race has just moved from, for the announcements that belong to a change and not to a state: a race
  // opened already finished must not sound its horn again. Declared first so the effects after it see this change.
  const lastStateRef = useRef<{ raceId: number | null; state: string | null | undefined }>({
    raceId: null,
    state: undefined,
  });
  const movedFromRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (!raceState) return;
    const last = lastStateRef.current;
    const prev = last.raceId === raceId ? last.state : undefined;
    lastStateRef.current = { raceId, state: raceState };
    movedFromRef.current = prev ?? undefined;
    if (!prev || prev === raceState || settingsRef.current?.announceRaceCalls === false) return;
    if (raceState === 'STOPPED') fallbackSpeak('Race stopped.');
    if (raceState === 'RUNNING' && prev === 'STOPPED') fallbackSpeak('Race resumed.');
    // Stop and resume lines
  }, [raceState, raceId, fallbackSpeak]);

  // A race the director abandoned ends with its own line, not the chequered flag
  const abandonedRef = useRef<number | null>(null);
  const markAbandoned = useCallback((abandonedRaceId: number) => {
    abandonedRef.current = abandonedRaceId;
  }, []);

  // Finish: the horn, then the spoken line (AUDIO-05). The horn and the line have a switch each.
  useEffect(() => {
    if (raceState !== 'FINISHED') return;
    // Only a race seen to finish: one opened already finished stays quiet
    if (movedFromRef.current !== 'RUNNING' && movedFromRef.current !== 'STOPPED') return;
    const s = settingsRef.current;
    if (abandonedRef.current === raceId) {
      if (s?.announceFinish !== false) fallbackSpeak('Race abandoned.');
      return;
    }
    const hornMs = s?.finishHorn !== false ? playFinishHorn(volumeRef.current) : 0;
    if (!s?.announceFinish) return;
    const t = setTimeout(() => playClip('finish', 'Race finished. Checkered flag.'), hornMs ? FINISH_HORN_MS : 0);
    return () => clearTimeout(t);
  }, [raceState, raceId, playClip, fallbackSpeak]);

  // The grid call: which race, and how many drivers, once the grid is known. Once per race.
  const gridSaidRef = useRef<number | null>(null);
  const gridCount = gridEntries?.length;
  useEffect(() => {
    if (raceState !== 'GRID' || raceId == null || !raceLabel || gridCount == null) return;
    if (gridSaidRef.current === raceId) return;
    gridSaidRef.current = raceId;
    if (settingsRef.current?.announceRaceCalls === false) return;
    const drivers = gridCount === 1 ? '1 driver' : `${gridCount} drivers`;
    fallbackSpeak(`${raceLabel}. ${drivers} to the grid.`);
  }, [raceState, raceId, raceLabel, gridCount, fallbackSpeak]);

  // Next race and its marshals, a moment after the finish line. Once per finished race.
  const nextSaidRef = useRef<number | null>(null);
  useEffect(() => {
    if (raceState !== 'FINISHED' || raceId == null || !nextRace) return;
    if (nextSaidRef.current === raceId) return;
    nextSaidRef.current = raceId;
    if (settingsRef.current?.announceNextRace === false) return;
    const marshals = nextRace.marshals.length > 0 ? ` Marshals: ${nextRace.marshals.join(', ')}.` : '';
    // Not cancelled by the effect rerunning, but dropped if the director has moved on
    setTimeout(() => {
      if (raceStateRef.current === 'FINISHED') fallbackSpeak(`Next race: ${nextRace.label}.${marshals}`);
    }, NEXT_RACE_DELAY_MS);
  }, [raceState, raceId, nextRace, fallbackSpeak]);

  // The stagger is called when the race starts (GRID to RUNNING), so each driver starts as their name is called
  // (AUDIO-03). It runs at most once per start, even if gridEntries refetches, and a race opened already
  // running does not replay it.
  const staggerArmedRef = useRef(false);
  const staggerFiredRef = useRef(false);
  const hornFiredRef = useRef(false);
  const clearStagger = useCallback(() => {
    staggerArmedRef.current = false;
    staggerFiredRef.current = false;
    hornFiredRef.current = false;
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
      hornFiredRef.current = false;
      return;
    }
    // A stop keeps the armed state: the race resumes without calling the drivers again
    if (raceState !== 'RUNNING') {
      if (raceState !== 'STOPPED') clearStagger();
      return;
    }
    if (!staggerArmedRef.current) return;

    // The start horn, once, then the line for a start with no driver calls
    let hornMs = 0;
    if (!hornFiredRef.current) {
      hornFiredRef.current = true;
      if (settingsRef.current?.startHorn !== false) hornMs = playStartHorn(volumeRef.current);
      if (startType && startType !== 'STAGGER' && settingsRef.current?.announceRaceCalls !== false) {
        const t = setTimeout(() => {
          if (raceStateRef.current === 'RUNNING') fallbackSpeak('Race started.');
        }, hornMs ? START_HORN_MS : 0);
        staggerTimeoutsRef.current.push(t);
      }
    }
    if (staggerFiredRef.current) return;
    if (startType !== 'STAGGER') return;
    if (!settingsRef.current?.announceStagger) return;
    if (!gridEntries || gridEntries.length === 0) return;

    staggerFiredRef.current = true;
    const stepMs = Math.max(staggerIntervalSeconds, 1) * 1000;
    // The first driver is called once the horn has sounded
    const startAfterMs = settingsRef.current?.startHorn !== false ? START_HORN_MS : 0;

    gridEntries.forEach((entry, index) => {
      const t = setTimeout(() => {
        if (raceStateRef.current !== 'RUNNING') return;
        if (!settingsRef.current?.announceStagger) return;
        playClip(gridClipKey(entry), gridFallbackText(entry));
      }, startAfterMs + index * stepMs);

      staggerTimeoutsRef.current.push(t);
    });
  }, [raceState, gridEntries, startType, staggerIntervalSeconds, clearStagger, playClip, fallbackSpeak]);

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
    markAbandoned,
    setClipMap,
  };
}
