import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useStartCountdown } from './useStartCountdown';

const playTick = vi.fn();
vi.mock('@/lib/sounds', () => ({ playTick: (...args: unknown[]) => playTick(...args) }));
const speak = vi.fn();
vi.mock('@/lib/speech', () => ({ speakWithBrowser: (...args: unknown[]) => speak(...args) }));

beforeEach(() => {
  vi.useFakeTimers();
  playTick.mockClear();
  speak.mockClear();
});
afterEach(() => vi.useRealTimers());

describe('useStartCountdown', () => {
  it('starts at once with no lead-in', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 0, volume: 1, onDone }));
    act(() => result.current.begin());
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(speak).not.toHaveBeenCalled();
  });

  it('says the lead-in, ticks the last five seconds, then starts', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 7, volume: 1, onDone }));
    act(() => result.current.begin());
    expect(speak).toHaveBeenCalledWith('Starting in 7 seconds.', 1);
    expect(result.current.remaining).toBe(7);
    expect(playTick).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(2000); });
    expect(result.current.remaining).toBe(5);
    expect(playTick).toHaveBeenCalledTimes(1);

    act(() => { vi.advanceTimersByTime(4000); });
    expect(result.current.remaining).toBe(1);
    expect(playTick).toHaveBeenLastCalledWith(1, true);
    expect(onDone).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(1000); });
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(result.current.remaining).toBeNull();
  });

  it('does not start when cancelled', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 3, volume: 1, onDone }));
    act(() => result.current.begin());
    act(() => result.current.cancel());
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(onDone).not.toHaveBeenCalled();
    expect(result.current.remaining).toBeNull();
  });
});
