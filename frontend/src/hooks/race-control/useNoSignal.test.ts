import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useNoSignal } from './useNoSignal';
import type { LapBarClock } from '@/components/LapProgressBar';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const NOW = 1_000_000;

function row(entryId: number, lastPassingTimeMs: number): LiveTimingRowDto {
  return {
    entryId, driverName: `Driver ${entryId}`, carNumber: null, position: entryId, lapsCompleted: 5, lastPassingTimeMs,
    lastLapMs: 15_000, bestLapMs: 15_000, avgLapMs: 15_000, overallFastestLapMs: null, lapsDown: 0,
    intervalLapsDown: 0, gapToLeaderMs: null, gapToAheadMs: null, totalTimeMs: null,
  };
}

describe('useNoSignal', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });
  afterEach(() => vi.useRealTimers());

  const quiet = [row(1, NOW - 60_000), row(2, NOW - 5_000)];

  it('flags a silent car once the clock is known, and not before the first interval', () => {
    const clock: LapBarClock = { offsetMs: 0, resumedAtMs: null, offsetKnown: true };
    const { result } = renderHook(() => useNoSignal(quiet, clock));
    expect([...result.current]).toEqual([]);
    act(() => { vi.advanceTimersByTime(1000); });
    expect([...result.current]).toEqual([1]);
  });

  it('flags nobody while the clock offset is not yet known', () => {
    const clock: LapBarClock = { offsetMs: 0, resumedAtMs: null, offsetKnown: false };
    const { result } = renderHook(() => useNoSignal(quiet, clock));
    act(() => { vi.advanceTimersByTime(3000); });
    expect(result.current.size).toBe(0);
  });

  it('counts the silence from a resume, and clears when the race is no longer running', () => {
    const clock: LapBarClock = { offsetMs: 0, resumedAtMs: NOW - 2_000, offsetKnown: true };
    const { result, rerender } = renderHook(({ c }) => useNoSignal(quiet, c), { initialProps: { c: clock as LapBarClock | undefined } });
    act(() => { vi.advanceTimersByTime(1000); });
    expect(result.current.size).toBe(0);

    rerender({ c: { offsetMs: 0, resumedAtMs: null, offsetKnown: true } });
    act(() => { vi.advanceTimersByTime(1000); });
    expect([...result.current]).toEqual([1]);

    rerender({ c: undefined });
    expect(result.current.size).toBe(0);
  });
});
