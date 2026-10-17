import { useCallback, useEffect, useRef, useState } from 'react';
import { fillWording, tuningOf, wordingOf, type AudioSettingsDto } from '@/lib/audioApi';
import { playTick } from '@/lib/sounds';
import { speakWithBrowser } from '@/lib/speech';

interface UseStartCountdownOptions {
  /** The lead-in in seconds; 0 starts at once. */
  seconds: number;
  volume: number;
  /** Called when the count reaches zero (or at once with no lead-in): sends the start command. */
  onDone: () => void;
  /** The club's audio settings: the wording of the call, whether it ticks, and how many seconds tick. */
  settings?: AudioSettingsDto | null;
  /** Changes when the count no longer belongs to what is on screen (another race, or the race's state moved). */
  watch: string;
}

/**
 * The lead-in between pressing Start and the race starting: a spoken "starting in N seconds", then a tick each
 * second for the last few (the club's final-seconds count). {@code remaining} is null when no count is running.
 */
export function useStartCountdown({ seconds, volume, onDone, watch, settings }: UseStartCountdownOptions) {
  const [remaining, setRemaining] = useState<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const onDoneRef = useRef(onDone);
  const volumeRef = useRef(volume);
  const settingsRef = useRef(settings);
  useEffect(() => {
    onDoneRef.current = onDone;
    volumeRef.current = volume;
    settingsRef.current = settings;
  }, [onDone, volume, settings]);

  const runningRef = useRef(false);
  // A count that only plays the lead-in (auto run starts the race itself) must not send the start command
  const leadInOnlyRef = useRef(false);

  const cancel = useCallback(() => {
    clearTimeout(timerRef.current);
    runningRef.current = false;
    setRemaining(null);
  }, []);

  // Selecting another race, or the race moving on, drops the count rather than starting the wrong race
  useEffect(() => cancel, [watch, cancel]);

  // The count calls itself a second later, so it lives in a ref
  const stepRef = useRef<(left: number) => void>(() => undefined);
  useEffect(() => {
    stepRef.current = (left: number) => {
      if (left <= 0) {
        runningRef.current = false;
        setRemaining(null);
        if (!leadInOnlyRef.current) onDoneRef.current();
        return;
      }
      setRemaining(left);
      const current = settingsRef.current;
      if (current?.tickSound !== false && left <= tuningOf(current).finalSeconds) {
        playTick(volumeRef.current, left === 1, current?.hornSounds);
      }
      timerRef.current = setTimeout(() => stepRef.current(left - 1), 1000);
    };
  }, []);

  const begin = useCallback(() => {
    if (runningRef.current) return;
    leadInOnlyRef.current = false;
    if (seconds <= 0) {
      onDoneRef.current();
      return;
    }
    runningRef.current = true;
    speakWithBrowser(fillWording(wordingOf(settingsRef.current, 'startLeadIn'), { seconds }), volumeRef.current);
    stepRef.current(seconds);
  }, [seconds]);

  /** Plays the lead-in from {@code from} seconds without starting the race, for auto run, which does that. */
  const leadIn = useCallback((from: number) => {
    if (runningRef.current || from <= 0) return;
    leadInOnlyRef.current = true;
    runningRef.current = true;
    speakWithBrowser(fillWording(wordingOf(settingsRef.current, 'startLeadIn'), { seconds: from }), volumeRef.current);
    stepRef.current(from);
  }, []);

  return { remaining, begin, leadIn, cancel };
}
