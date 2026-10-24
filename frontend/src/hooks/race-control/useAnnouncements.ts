import { useCallback, useEffect, useRef } from 'react';
import { useStomp } from '@/hooks/race-control/useStomp';
import { fillWording, tuningOf, wordingOf, type AudioSettingsDto } from '@/lib/audioApi';
import { startHornMs, type LapPace } from '@/lib/sounds';
import { routeCue } from '@/lib/audioOutput';
import { firstNameOf } from '@/lib/speechNames';
import type { SoundRoute } from '@/lib/audioRouting';

const DEFAULT_COUNTDOWN_INTERVALS = [600, 300, 120, 60, 30];

type AudioEvent =
  | { type: 'running-order'; positions: string[] }
  /** Auto run (#338): the time to the next race, or that it is holding for the finals. */
  | { type: 'next-race-warning' | 'auto-run-held'; text: string }
  /** Auto run is about to start the race: the lead-in count. */
  | { type: 'start-countdown'; seconds: number };

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

/** Said when the race clock reaches zero, until the club words its own. */
const DEFAULT_TIME_UP_TEXT = 'Time. Cars finish as they cross the line.';

/** Clip key the server stores an entry's grid call under (AudioPreGenerationService). */
export function gridClipKey(entry: GridEntry): string {
  return `grid-${entry.entryId}`;
}

/** What the browser says for a grid call when no clip is available. */
export function gridFallbackText(entry: GridEntry, firstNamesOnly = false): string {
  const whole = entry.speechName?.trim() || entry.driverName;
  const name = firstNamesOnly ? firstNameOf(whole) : whole;
  return entry.carNumber ? `Car ${entry.carNumber}, ${name}.` : `${name}.`;
}

/** What the browser says as a car takes the flag when no clip is available: the club's wording of the call. */
export function carFinishText(entry: GridEntry, settings: AudioSettingsDto | null | undefined): string {
  const name = entry.speechName?.trim() || entry.driverName;
  const template = wordingOf(settings, 'carFinish');
  // A car with no number is called by name alone, as a grid call is
  if (!entry.carNumber && template.includes('{car}')) return `${name}.`;
  return fillWording(template, { car: entry.carNumber ?? '', name });
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
  /** Called when auto run sends the start lead-in, with the seconds left before the race starts. */
  onStartCountdown?: (seconds: number) => void;
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
  onStartCountdown,
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
  const { data: audioEvent } = useStomp<AudioEvent>(
    raceId ? `/topic/race/${raceId}/audio` : null,
  );

  // Web Speech API fallback (AUDIO-11), on the outputs the club chose for this kind of line (#478)
  const fallbackSpeak = useCallback((text: string, route: SoundRoute = 'raceCalls') => {
    routeCue(route, volumeRef.current, { kind: 'speech', text });
  }, []);

  // playClip: play a pre-generated clip URL; fall back to the spoken text (AUDIO-10, AUDIO-11)
  const playClip = useCallback((key: string, fallbackText: string, route: SoundRoute = 'raceCalls') => {
    routeCue(route, volumeRef.current, { kind: 'clip', url: clipMapRef.current[key] ?? null, text: fallbackText });
  }, []);

  // setClipMap: populate the clip cache from outside (called by usePregeneratedClips)
  const setClipMap = useCallback((map: Record<string, string>) => {
    clipMapRef.current = map;
  }, []);

  // A different race has different clips: drop the previous race's URLs straight away
  useEffect(() => {
    clipMapRef.current = {};
  }, [raceId]);

  const onStartCountdownRef = useRef(onStartCountdown);
  useEffect(() => { onStartCountdownRef.current = onStartCountdown; }, [onStartCountdown]);

  // Handle auto run's cues (#338): the time to the next race, the hold for the finals, and the start lead-in
  useEffect(() => {
    if (!audioEvent) return;
    if (audioEvent.type === 'start-countdown') {
      onStartCountdownRef.current?.(audioEvent.seconds);
    } else if (audioEvent.type === 'next-race-warning' || audioEvent.type === 'auto-run-held') {
      if (settingsRef.current?.announceNextRace !== false) fallbackSpeak(audioEvent.text, 'nextRace');
    }
  }, [audioEvent, fallbackSpeak]);

  // Handle running-order announcements (AUDIO-06)
  useEffect(() => {
    if (!audioEvent) return;
    const s = settingsRef.current;
    if (!s?.announceRunningOrder) return;
    if (audioEvent.type === 'running-order' && audioEvent.positions?.length > 0) {
      const positions = audioEvent.positions.map((name, i) => `${i + 1}, ${name}`).join('. ');
      fallbackSpeak(fillWording(wordingOf(s, 'runningOrder'), { positions }), 'runningOrder');
    }
  }, [audioEvent, fallbackSpeak]);

  // A change to the call times or the final-seconds count mid-race applies to the calls still to come
  const countdownKey = `${settings?.countdownIntervals?.join(',')}:${settings?.tuning?.finalSeconds}`;

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
        playClip(`countdown-${secsBeforeEnd}`, text, 'countdown');
      }, delay);

      countdownTimeoutsRef.current.push(t);
    });

    // A beep for each of the club's final seconds, the last one higher; off unless the club switches it on
    if (s.finalBeeps === true) {
      const finalSeconds = tuningOf(s).finalSeconds;
      for (let left = finalSeconds; left >= 1; left--) {
        const delay = raceEndsAt - left * 1000 - now;
        if (delay <= 0) continue;
        countdownTimeoutsRef.current.push(setTimeout(() => {
          if (raceStateRef.current !== 'RUNNING') return;
          if (settingsRef.current?.finalBeeps !== true) return;
          routeCue('tick', volumeRef.current, {
            kind: 'horn', horn: left === 1 ? 'tickLast' : 'tick', sounds: settingsRef.current?.hornSounds,
          });
        }, delay));
      }
    }

    // At zero the clock is up for the first car: each car now finishes as it crosses the line
    const upDelay = raceEndsAt - now;
    if (upDelay > 0) {
      countdownTimeoutsRef.current.push(setTimeout(() => {
        if (raceStateRef.current !== 'RUNNING') return;
        if (!settingsRef.current?.announceCountdown) return;
        fallbackSpeak(settingsRef.current?.timeUpText?.trim() || DEFAULT_TIME_UP_TEXT, 'countdown');
      }, upDelay));
    }

    return () => {
      countdownTimeoutsRef.current.forEach(clearTimeout);
      countdownTimeoutsRef.current = [];
    };
  }, [raceState, raceEndsAt, playClip, fallbackSpeak, countdownKey]);

  // What the race has just moved from, for the announcements that belong to a change and not to a state: a race
  // opened already finished must not sound its horn again. Declared first so the effects after it see this change.
  const lastStateRef = useRef<{ raceId: number | null; state: string | null | undefined }>({
    raceId: null,
    state: undefined,
  });
  const movedFromRef = useRef<string | undefined>(undefined);
  const gridSaidRef = useRef<number | null>(null);
  const raceIdRef = useRef(raceId);
  useEffect(() => { raceIdRef.current = raceId; }, [raceId]);
  useEffect(() => {
    if (!raceState) return;
    const last = lastStateRef.current;
    const prev = last.raceId === raceId ? last.state : undefined;
    lastStateRef.current = { raceId, state: raceState };
    movedFromRef.current = prev ?? undefined;
    // A race that leaves the grid may be called to it again
    if (raceState !== 'GRID') gridSaidRef.current = null;
    // Stop and resume lines
    if (!prev || prev === raceState || settingsRef.current?.announceRaceCalls === false) return;
    if (raceState === 'STOPPED') fallbackSpeak(wordingOf(settingsRef.current, 'stop'));
    if (raceState === 'RUNNING' && prev === 'STOPPED') fallbackSpeak(wordingOf(settingsRef.current, 'resume'));
  }, [raceState, raceId, fallbackSpeak]);

  // A race the director abandoned ends with its own line, not the chequered flag
  const abandonedRef = useRef<number | null>(null);
  const markAbandoned = useCallback((abandonedRaceId: number) => {
    abandonedRef.current = abandonedRaceId;
  }, []);
  // For an abandon the server refused
  const clearAbandoned = useCallback(() => {
    abandonedRef.current = null;
  }, []);

  // Finish: the horn, then the spoken line (AUDIO-05). The horn and the line have a switch each.
  useEffect(() => {
    if (raceState !== 'FINISHED') return;
    // Only a race seen to finish: one opened already finished stays quiet
    if (movedFromRef.current !== 'RUNNING' && movedFromRef.current !== 'STOPPED') return;
    const s = settingsRef.current;
    if (abandonedRef.current === raceId) {
      abandonedRef.current = null;
      if (s?.announceFinish !== false) fallbackSpeak(wordingOf(s, 'abandon'));
      return;
    }
    const hornMs = s?.finishHorn !== false
      ? routeCue('finishHorn', volumeRef.current, { kind: 'horn', horn: 'finish', sounds: s?.hornSounds })
      : 0;
    if (!s?.announceFinish) return;
    const t = setTimeout(() => playClip('finish', wordingOf(s, 'raceFinished'), 'raceFinished'), hornMs);
    return () => clearTimeout(t);
  }, [raceState, raceId, playClip, fallbackSpeak]);

  // The grid call: which race, and how many drivers, once the grid is known. Once per race.
  const gridCount = gridEntries?.length;
  useEffect(() => {
    if (raceState !== 'GRID' || raceId == null || !raceLabel || gridCount == null) return;
    // Only a race seen to be called to the grid: one opened already on it stays quiet
    if (movedFromRef.current !== 'PENDING') return;
    if (gridSaidRef.current === raceId) return;
    gridSaidRef.current = raceId;
    if (settingsRef.current?.announceRaceCalls === false) return;
    const drivers = gridCount === 1 ? '1 driver' : `${gridCount} drivers`;
    fallbackSpeak(fillWording(wordingOf(settingsRef.current, 'gridCall'), { race: raceLabel, drivers }));
  }, [raceState, raceId, raceLabel, gridCount, fallbackSpeak]);

  // Next race and its marshals, a moment after the finish line. Once per finished race.
  const nextSaidRef = useRef<number | null>(null);
  const nextTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => {
    if (raceState !== 'FINISHED' || raceId == null || !nextRace) return;
    if (movedFromRef.current !== 'RUNNING' && movedFromRef.current !== 'STOPPED') return;
    if (nextSaidRef.current === raceId) return;
    nextSaidRef.current = raceId;
    if (settingsRef.current?.announceNextRace === false) return;
    const s = settingsRef.current;
    const marshals = nextRace.marshals.length > 0
      ? ` ${fillWording(wordingOf(s, 'marshals'), { marshals: nextRace.marshals.join(', ') })}`
      : '';
    // Not cancelled by the effect rerunning, but dropped if the director has moved on to another race
    clearTimeout(nextTimerRef.current);
    nextTimerRef.current = setTimeout(() => {
      if (raceStateRef.current === 'FINISHED' && raceIdRef.current === raceId) {
        fallbackSpeak(`${fillWording(wordingOf(s, 'nextRace'), { race: nextRace.label })}${marshals}`, 'nextRace');
      }
    }, NEXT_RACE_DELAY_MS);
  }, [raceState, raceId, nextRace, fallbackSpeak]);
  useEffect(() => () => clearTimeout(nextTimerRef.current), [raceId]);

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
      if (settingsRef.current?.startHorn !== false) {
        hornMs = routeCue('startHorn', volumeRef.current, {
          kind: 'horn', horn: 'start', sounds: settingsRef.current?.hornSounds,
        });
      }
      if (startType && startType !== 'STAGGER' && settingsRef.current?.announceRaceCalls !== false) {
        const t = setTimeout(() => {
          if (raceStateRef.current === 'RUNNING') fallbackSpeak(wordingOf(settingsRef.current, 'go'));
        }, hornMs);
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
    const startAfterMs = settingsRef.current?.startHorn !== false ? startHornMs(settingsRef.current?.hornSounds) : 0;

    gridEntries.forEach((entry, index) => {
      const t = setTimeout(() => {
        if (raceStateRef.current !== 'RUNNING') return;
        if (!settingsRef.current?.announceStagger) return;
        playClip(gridClipKey(entry),
          gridFallbackText(entry, tuningOf(settingsRef.current).firstNamesOnly), 'stagger');
      }, startAfterMs + index * stepMs);

      staggerTimeoutsRef.current.push(t);
    });
  }, [raceState, gridEntries, startType, staggerIntervalSeconds, clearStagger, playClip, fallbackSpeak]);

  // Per-lap beeps (AUDIO-04): by pace, or one plain beep when the club switches pace beeps off
  const playBeep = useCallback(
    (pace: LapPace) => {
      if (settingsRef.current && !settingsRef.current.announceLapBeep) return;
      const tuning = tuningOf(settingsRef.current);
      // Pace beeps off: every lap gets the plain beep, with no prediction of better or worse
      const plain = settingsRef.current?.paceBeeps === false;
      routeCue('lapBeep', volumeRef.current, {
        kind: 'lapBeep',
        pace: plain ? 'onPace' : pace,
        tone: { hz: tuning.lapBeepHz, ms: tuning.lapBeepMs },
        sounds: settingsRef.current?.hornSounds,
      });
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

  // A car has taken the flag: its number and name, as the grid call said them
  const gridEntriesRef = useRef(gridEntries);
  useEffect(() => { gridEntriesRef.current = gridEntries; }, [gridEntries]);
  /** Returns false when the call could not be made yet because the grid is not loaded, so it can be tried again. */
  const announceCarFinish = useCallback((entryId: number): boolean => {
    if (settingsRef.current?.announceCarFinish === false) return true;
    const state = raceStateRef.current;
    if (state !== 'RUNNING' && state !== 'FINISHED') return true;
    const entry = gridEntriesRef.current?.find((e) => e.entryId === entryId);
    if (!entry) return false;
    playClip(`car-finish-${entryId}`, carFinishText(entry, settingsRef.current), 'carFinish');
    return true;
  }, [playClip]);

  return {
    fallbackSpeak,
    playBeep,
    announceCarFinish,
    playClip,
    markAbandoned,
    clearAbandoned,
    setClipMap,
  };
}
