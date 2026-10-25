import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useGapView } from './useGapView';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('useGapView', () => {
  it('holds a fixed mode', () => {
    const { result } = renderHook(() => useGapView('leader', 5));
    act(() => vi.advanceTimersByTime(20_000));
    expect(result.current).toEqual({ view: 'leader', fading: false });
  });

  it('takes turns every period, fading across the change', () => {
    const { result } = renderHook(() => useGapView('auto', 5));
    expect(result.current.view).toBe('int');
    act(() => vi.advanceTimersByTime(5_000));
    expect(result.current).toEqual({ view: 'int', fading: true });
    act(() => vi.advanceTimersByTime(300));
    expect(result.current).toEqual({ view: 'leader', fading: false });
    act(() => vi.advanceTimersByTime(5_000 + 300));
    expect(result.current.view).toBe('int');
  });

  it('does not restart its timer when the component re-renders', () => {
    const { result, rerender } = renderHook(() => useGapView('auto', 5));
    act(() => vi.advanceTimersByTime(4_000));
    rerender();
    act(() => vi.advanceTimersByTime(1_300));
    expect(result.current.view).toBe('leader');
  });
});
