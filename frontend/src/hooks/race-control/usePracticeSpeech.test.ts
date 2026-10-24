import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { PracticeTimingRowDto } from '@/lib/practiceApi';

const routeCue = vi.hoisted(() => vi.fn());
vi.mock('@/lib/audioOutput', () => ({ routeCue }));

import { usePracticeSpeech } from './usePracticeSpeech';

function row(laps: number, lastLapMs = 12340): PracticeTimingRowDto {
  return {
    position: 1, transponderNumber: '1001', userId: null, racerName: 'Alex Rowe', laps, bestLapMs: lastLapMs,
    bestConsecutiveNMs: null, lastLapMs, runs: 1, isUnknown: false,
  };
}

describe('usePracticeSpeech', () => {
  beforeEach(() => {
    routeCue.mockReset();
    localStorage.clear();
  });

  it('says nothing until it is switched on', () => {
    const { rerender } = renderHook(({ rows }) => usePracticeSpeech(rows, true, true), {
      initialProps: { rows: [row(1)] },
    });

    rerender({ rows: [row(2)] });

    expect(routeCue).not.toHaveBeenCalled();
  });

  it('says a lap as it is completed, but not the laps already run when the table loaded', () => {
    localStorage.setItem('rc-practice-speech', JSON.stringify({ on: true, firstNames: true }));
    const { rerender } = renderHook(({ rows }) => usePracticeSpeech(rows, true, true), {
      initialProps: { rows: [row(5)] },
    });
    expect(routeCue).not.toHaveBeenCalled();

    rerender({ rows: [row(6, 11000)] });

    expect(routeCue).toHaveBeenCalledTimes(1);
    expect(routeCue).toHaveBeenCalledWith('practiceLaps', 1, { kind: 'speech', text: 'Alex, 11.00' });
  });

  it('keeps quiet while the table is still loading', () => {
    localStorage.setItem('rc-practice-speech', JSON.stringify({ on: true, firstNames: false }));
    const { rerender } = renderHook(({ rows, ready }) => usePracticeSpeech(rows, true, ready), {
      initialProps: { rows: [] as PracticeTimingRowDto[], ready: false },
    });

    rerender({ rows: [row(5)], ready: true });

    expect(routeCue).not.toHaveBeenCalled();
  });

  it('remembers the choice in this browser', () => {
    const { result } = renderHook(() => usePracticeSpeech([], true, true));

    act(() => result.current.update({ on: true }));

    expect(JSON.parse(localStorage.getItem('rc-practice-speech')!)).toEqual({ on: true, firstNames: false });
  });
});
