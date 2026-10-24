import { useCallback } from 'react';
import type { RaceClockDto } from '@/lib/boardsApi';

/**
 * Turns a polled race clock into "time left at this device time": the remaining time when it was read, less the
 * time since while the clock is counting. Null while the race has no time limit or no clock has arrived.
 *
 * @param clock the last race clock answer
 * @param readAtMs when this device received it, in milliseconds
 */
export function useRemainingAt(clock: RaceClockDto | undefined, readAtMs: number): (deviceNowMs: number) => number | null {
  return useCallback((deviceNowMs: number) => {
    if (!clock || clock.remainingMs === null) return null;
    return clock.remainingMs - (clock.running ? Math.max(0, deviceNowMs - readAtMs) : 0);
  }, [clock, readAtMs]);
}
