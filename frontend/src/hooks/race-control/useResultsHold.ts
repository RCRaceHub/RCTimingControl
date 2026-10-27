import { useCallback, useSyncExternalStore } from 'react';

const STORAGE_KEY = 'rc-results-hold-seconds';
export const DEFAULT_RESULTS_HOLD_SECONDS = 30;
export const MAX_RESULTS_HOLD_SECONDS = 600;

const listeners = new Set<() => void>();
// What was last set, for when storage refuses it (a private window), so the field still changes
let unsaved: number | null = null;

function read(): number {
  if (unsaved != null) return unsaved;
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    const seconds = stored == null ? NaN : parseInt(stored, 10);
    return Number.isFinite(seconds)
      ? Math.min(MAX_RESULTS_HOLD_SECONDS, Math.max(0, seconds))
      : DEFAULT_RESULTS_HOLD_SECONDS;
  } catch {
    return DEFAULT_RESULTS_HOLD_SECONDS;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  window.addEventListener('storage', listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', listener);
  };
}

/**
 * How long, in seconds, the cockpit stays on a race's results after auto run moves on to the next race, so the
 * race director has time to read them. Kept in localStorage on this browser, like the announcer volume, since
 * it is about what this screen shows. 0 moves on at once.
 */
export function useResultsHold(): [number, (seconds: number) => void] {
  const seconds = useSyncExternalStore(subscribe, read, () => DEFAULT_RESULTS_HOLD_SECONDS);
  const setSeconds = useCallback((next: number) => {
    try {
      localStorage.setItem(STORAGE_KEY, String(next));
      unsaved = null;
    } catch {
      unsaved = next;
    }
    listeners.forEach((listener) => listener());
  }, []);
  return [seconds, setSeconds];
}
