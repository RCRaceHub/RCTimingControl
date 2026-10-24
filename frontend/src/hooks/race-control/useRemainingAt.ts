import { useCallback } from 'react';
import type { RaceClockDto } from '@/lib/boardsApi';

/**
 * Turns a polled race clock into "time left at this device time": the remaining time when it was read, less the
 * time since while the clock is counting. Null while the race has no time limit, no clock has arrived or the clock is not counting.
 *
 * @param clock the last race clock answer
 * @param readAtMs when this device received it, in milliseconds
 */
export function useRemainingAt(clock: RaceClockDto | undefined, readAtMs: number): (deviceNowMs: number) => number | null {
  return useCallback((deviceNowMs: number) => {
    // A stopped or finished race has no pace to project
    if (!clock || clock.remainingMs === null || !clock.running) return null;
    return clock.remainingMs - Math.max(0, deviceNowMs - readAtMs);
  }, [clock, readAtMs]);
}
