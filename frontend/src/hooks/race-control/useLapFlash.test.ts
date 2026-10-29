import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useLapFlash } from './useLapFlash';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

function row(entryId: number, overrides: Partial<LiveTimingRowDto> = {}): LiveTimingRowDto {
  return {
    entryId, driverName: `Driver ${entryId}`, carNumber: null, position: entryId, lapsCompleted: 0, lastPassingTimeMs: 0,
    lastLapMs: null, bestLapMs: null, avgLapMs: null, overallFastestLapMs: null, lapsDown: 0,
    intervalLapsDown: 0, gapToLeaderMs: null, gapToAheadMs: null, totalTimeMs: null, ...overrides,
  };
}

describe('useLapFlash', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('flashes nothing before any lap', () => {
    const { result } = renderHook(() => useLapFlash([row(1), row(2)]));
    expect(result.current.size).toBe(0);
  });

  it('classifies each new lap', () => {
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), {
      initialProps: { rows: [row(1), row(2), row(3), row(4)] },
    });

    rerender({
      rows: [
        // fastest of the race
        row(1, { lapsCompleted: 2, lastLapMs: 15000, bestLapMs: 15000, overallFastestLapMs: 15000 }),
        // personal best, not the race's fastest
        row(2, { lapsCompleted: 1, lastLapMs: 16000, bestLapMs: 16000, overallFastestLapMs: 15000 }),
        // well above the running average
        row(3, { lapsCompleted: 1, lastLapMs: 20000, bestLapMs: 15500, avgLapMs: 17000, overallFastestLapMs: 15000 }),
        // first lap seen for this driver, not a best, not slow
        row(4, { lapsCompleted: 1, lastLapMs: 16500, bestLapMs: 16200, avgLapMs: 16400, overallFastestLapMs: 15000 }),
      ],
    });

    expect(result.current.get(1)).toBe('race-best');
    expect(result.current.get(2)).toBe('personal-best');
    expect(result.current.get(3)).toBe('slow');
    expect(result.current.get(4)).toBe('improving');
  });

  it('does not give the first lap, from a standing start, the race-best star', () => {
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), { initialProps: { rows: [row(1)] } });
    rerender({ rows: [row(1, { lapsCompleted: 1, lastLapMs: 15000, bestLapMs: 15000, overallFastestLapMs: 15000 })] });
    expect(result.current.get(1)).toBe('personal-best');
  });

  it('compares with the driver\'s previous lap', () => {
    const base = { bestLapMs: 15000, avgLapMs: 16500, overallFastestLapMs: 14000 };
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), {
      initialProps: { rows: [row(1, { lapsCompleted: 1, lastLapMs: 16000, ...base })] },
    });

    rerender({ rows: [row(1, { lapsCompleted: 2, lastLapMs: 16200, ...base })] });
    expect(result.current.get(1)).toBe('slow');

    rerender({ rows: [row(1, { lapsCompleted: 3, lastLapMs: 15800, ...base })] });
    expect(result.current.get(1)).toBe('improving');
  });

  it('does not flash again when the lap count has not changed', () => {
    const lap = row(1, { lapsCompleted: 1, lastLapMs: 15000, bestLapMs: 15000 });
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), {
      initialProps: { rows: [row(1)] },
    });
    rerender({ rows: [lap] });
    act(() => vi.advanceTimersByTime(2500));
    expect(result.current.size).toBe(0);

    rerender({ rows: [{ ...lap, position: 2 }] });
    expect(result.current.size).toBe(0);
  });

  it('clears a flash after 2.5 seconds, restarting the time on a new lap', () => {
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), {
      initialProps: { rows: [row(1)] },
    });
    rerender({ rows: [row(1, { lapsCompleted: 1, lastLapMs: 15000, bestLapMs: 15000 })] });
    act(() => vi.advanceTimersByTime(2000));
    rerender({ rows: [row(1, { lapsCompleted: 2, lastLapMs: 14900, bestLapMs: 14900 })] });
    act(() => vi.advanceTimersByTime(2000));
    expect(result.current.get(1)).toBe('personal-best');

    act(() => vi.advanceTimersByTime(500));
    expect(result.current.size).toBe(0);
  });

  it('ignores an empty update', () => {
    const { result, rerender } = renderHook(({ rows }) => useLapFlash(rows), {
      initialProps: { rows: [row(1, { lapsCompleted: 1, lastLapMs: 15000, bestLapMs: 15000 })] },
    });
    rerender({ rows: [] });
    rerender({ rows: [row(1, { lapsCompleted: 1, lastLapMs: 15000, bestLapMs: 15000 })] });
    act(() => vi.advanceTimersByTime(2500));
    expect(result.current.size).toBe(0);
  });
});
