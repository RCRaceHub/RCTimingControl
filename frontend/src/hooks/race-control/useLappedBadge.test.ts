import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useLappedBadge } from './useLappedBadge';

const laps = (...counts: number[]) => counts.map((lapsCompleted, i) => ({ entryId: i + 1, lapsCompleted }));

describe('useLappedBadge', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('marks nobody while everyone is on the lead lap', () => {
    const rows = laps(5, 5, 5);
    const { result } = renderHook(() => useLappedBadge(rows));
    act(() => vi.advanceTimersByTime(0));
    expect(result.current.size).toBe(0);
  });

  it('marks nobody while any car is still on lap 0', () => {
    const { result } = renderHook(() => useLappedBadge(laps(2, 1, 0), 5000));
    act(() => vi.advanceTimersByTime(10_000));
    expect(result.current.size).toBe(0);
  });

  it('marks a car only after it has been a lap down for the whole debounce', () => {
    const { result, rerender } = renderHook(({ rows }) => useLappedBadge(rows, 5000), {
      initialProps: { rows: laps(6, 5) },
    });
    act(() => vi.advanceTimersByTime(4000));
    rerender({ rows: laps(6, 5) });
    expect(result.current.has(2)).toBe(false);

    act(() => vi.advanceTimersByTime(1000));
    rerender({ rows: laps(6, 5) });
    act(() => vi.advanceTimersByTime(0));
    expect([...result.current]).toEqual([2]);
  });

  it('forgets a car that catches back up to the lead lap', () => {
    const { result, rerender } = renderHook(({ rows }) => useLappedBadge(rows, 5000), {
      initialProps: { rows: laps(6, 5) },
    });
    act(() => vi.advanceTimersByTime(3000));
    rerender({ rows: laps(6, 6) });
    act(() => vi.advanceTimersByTime(3000));
    rerender({ rows: laps(7, 6) });
    act(() => vi.advanceTimersByTime(0));
    // Lapped again only 0 s ago, not 6 s
    expect(result.current.size).toBe(0);
  });

  it('clears everything when the rows empty', () => {
    const { result, rerender } = renderHook(({ rows }) => useLappedBadge(rows, 5000), {
      initialProps: { rows: laps(6, 5) },
    });
    act(() => vi.advanceTimersByTime(5000));
    rerender({ rows: laps(6, 5) });
    act(() => vi.advanceTimersByTime(0));
    expect(result.current.size).toBe(1);

    rerender({ rows: [] });
    act(() => vi.advanceTimersByTime(0));
    expect(result.current.size).toBe(0);
  });

  it('shows the badge once the debounce passes even if the timing does not change', () => {
    const rows = laps(6, 5);
    const { result } = renderHook(() => useLappedBadge(rows, 5000));
    act(() => vi.advanceTimersByTime(4999));
    expect(result.current.size).toBe(0);

    act(() => vi.advanceTimersByTime(1));
    expect([...result.current]).toEqual([2]);
  });
});
