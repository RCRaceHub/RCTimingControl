import { useCallback, useEffect, useRef, useState } from 'react';
import { playTick } from '@/lib/sounds';
import { speakWithBrowser } from '@/lib/speech';

/** From this many seconds out, every second ticks. */
const TICK_FROM = 5;

interface UseStartCountdownOptions {
  /** The lead-in in seconds; 0 starts at once. */
  seconds: number;
  volume: number;
  /** Called when the count reaches zero (or at once with no lead-in): sends the start command. */
  onDone: () => void;
  /** Changes when the count no longer belongs to what is on screen (another race, or the race's state moved). */
  watch: string;
}

/**
 * The lead-in between pressing Start and the race starting: a spoken "starting in N seconds", then a tick each
 * second for the last five. {@code remaining} is null when no count is running.
 */
export function useStartCountdown({ seconds, volume, onDone, watch }: UseStartCountdownOptions) {
  const [remaining, setRemaining] = useState<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const onDoneRef = useRef(onDone);
  const volumeRef = useRef(volume);
  useEffect(() => {
    onDoneRef.current = onDone;
    volumeRef.current = volume;
  }, [onDone, volume]);

  const runningRef = useRef(false);

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
        onDoneRef.current();
        return;
      }
      setRemaining(left);
      if (left <= TICK_FROM) playTick(volumeRef.current, left === 1);
      timerRef.current = setTimeout(() => stepRef.current(left - 1), 1000);
    };
  }, []);

  const begin = useCallback(() => {
    if (runningRef.current) return;
    if (seconds <= 0) {
      onDoneRef.current();
      return;
    }
    runningRef.current = true;
    speakWithBrowser(`Starting in ${seconds} seconds.`, volumeRef.current);
    stepRef.current(seconds);
  }, [seconds]);

  return { remaining, begin, cancel };
}
