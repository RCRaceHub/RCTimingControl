import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { AudioSettingsDto } from '@/lib/audioApi';
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
    const { result } = renderHook(() => useStartCountdown({ seconds: 0, volume: 1, onDone, watch: 'a' }));
    act(() => result.current.begin());
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(speak).not.toHaveBeenCalled();
  });

  it('says the lead-in, ticks the last five seconds, then starts', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 7, volume: 1, onDone, watch: 'a' }));
    act(() => result.current.begin());
    expect(speak).toHaveBeenCalledWith('Starting in 7 seconds.', 1);
    expect(result.current.remaining).toBe(7);
    expect(playTick).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(2000); });
    expect(result.current.remaining).toBe(5);
    expect(playTick).toHaveBeenCalledTimes(1);

    act(() => { vi.advanceTimersByTime(4000); });
    expect(result.current.remaining).toBe(1);
    expect(playTick).toHaveBeenLastCalledWith(1, true, undefined);
    expect(onDone).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(1000); });
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(result.current.remaining).toBeNull();
  });

  it('plays the lead-in for auto run without sending the start', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 10, volume: 1, onDone, watch: 'a' }));
    act(() => result.current.leadIn(3));
    expect(speak).toHaveBeenCalledWith('Starting in 3 seconds.', 1);
    expect(result.current.remaining).toBe(3);

    act(() => { vi.advanceTimersByTime(3000); });
    expect(result.current.remaining).toBeNull();
    expect(onDone).not.toHaveBeenCalled();
  });

  it('does not start when cancelled', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 3, volume: 1, onDone, watch: 'a' }));
    act(() => result.current.begin());
    act(() => result.current.cancel());
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(onDone).not.toHaveBeenCalled();
    expect(result.current.remaining).toBeNull();
  });

  it('drops the count when another race is selected', () => {
    const onDone = vi.fn();
    const { result, rerender } = renderHook(
      ({ watch }: { watch: string }) => useStartCountdown({ seconds: 3, volume: 1, onDone, watch }),
      { initialProps: { watch: 'race-1:GRID' } },
    );
    act(() => result.current.begin());
    rerender({ watch: 'race-2:GRID' });
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(onDone).not.toHaveBeenCalled();
    expect(result.current.remaining).toBeNull();
  });

  it('ignores a second press while counting', () => {
    const onDone = vi.fn();
    const { result } = renderHook(() => useStartCountdown({ seconds: 3, volume: 1, onDone, watch: 'a' }));
    act(() => result.current.begin());
    act(() => result.current.begin());
    expect(speak).toHaveBeenCalledTimes(1);
    act(() => { vi.advanceTimersByTime(3000); });
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('says the clubs own lead-in and ticks the seconds it asks for', () => {
    const onDone = vi.fn();
    const settings = { wording: { startLeadIn: 'Get ready, {seconds} to go.' }, tuning: { finalSeconds: 2 } } as AudioSettingsDto;
    const { result } = renderHook(() => useStartCountdown({ seconds: 4, volume: 1, onDone, watch: 'a', settings }));
    act(() => result.current.begin());
    expect(speak).toHaveBeenCalledWith('Get ready, 4 to go.', 1);

    act(() => { vi.advanceTimersByTime(2000); });
    expect(playTick).toHaveBeenCalledTimes(1);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(playTick).toHaveBeenCalledTimes(2);
  });

  it('does not tick when the ticks are switched off', () => {
    const onDone = vi.fn();
    const settings = { tickSound: false } as AudioSettingsDto;
    const { result } = renderHook(() => useStartCountdown({ seconds: 3, volume: 1, onDone, watch: 'a', settings }));
    act(() => result.current.begin());
    act(() => { vi.advanceTimersByTime(3000); });
    expect(playTick).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledTimes(1);
  });
});
