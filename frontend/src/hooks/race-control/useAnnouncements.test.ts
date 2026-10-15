import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import {
  useAnnouncements,
  gridClipKey,
  gridFallbackText,
  type GridEntry,
} from './useAnnouncements';
import type { AudioSettingsDto } from '@/lib/audioApi';

vi.mock('@/hooks/race-control/useStomp', () => ({ useStomp: () => ({ data: null }) }));

const playStartHorn = vi.fn<(volume: number) => number>(() => 1200);
const playFinishHorn = vi.fn<(volume: number) => number>(() => 1800);
vi.mock('@/lib/sounds', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/sounds')>()),
  playStartHorn: (v: number) => playStartHorn(v),
  playFinishHorn: (v: number) => playFinishHorn(v),
}));

const settings = {
  announceCountdown: true,
  announceStagger: true,
  announceLapBeep: true,
  announceFinish: true,
  announceRunningOrder: true,
  startHorn: true,
  finishHorn: true,
  announceRaceCalls: true,
  announceNextRace: true,
} as AudioSettingsDto;

const grid: GridEntry[] = [
  { entryId: 11, carNumber: null, driverName: 'Alex Rowe' },
  { entryId: 12, carNumber: null, driverName: 'Sam Ito' },
];

const audioPlay = vi.fn(() => Promise.resolve());
const audioUrls: string[] = [];
const speak = vi.fn();

beforeEach(() => {
  vi.useFakeTimers();
  audioPlay.mockClear();
  playStartHorn.mockClear();
  playFinishHorn.mockClear();
  speak.mockClear();
  audioUrls.length = 0;
  vi.stubGlobal(
    'Audio',
    class {
      volume = 1;
      constructor(url: string) { audioUrls.push(url); }
      play = audioPlay;
    },
  );
  vi.stubGlobal('SpeechSynthesisUtterance', class { volume = 1; text: string; constructor(text: string) { this.text = text; } });
  Object.defineProperty(window, 'speechSynthesis', { value: { speak }, configurable: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('grid call helpers', () => {
  it('keys the clip by entry id', () => {
    expect(gridClipKey(grid[0])).toBe('grid-11');
  });
  it('falls back to the name, with the car number when there is one', () => {
    expect(gridFallbackText(grid[0])).toBe('Alex Rowe.');
    expect(gridFallbackText({ ...grid[0], carNumber: '7' })).toBe('Car 7, Alex Rowe.');
  });
  it('says what the server says to say, when it sends it', () => {
    expect(gridFallbackText({ ...grid[0], speechName: 'Al-ex Roe' })).toBe('Al-ex Roe.');
    expect(gridFallbackText({ ...grid[0], speechName: '  ' })).toBe('Alex Rowe.');
  });
});

describe('useAnnouncements stagger start', () => {
  type P = { raceState: string; startType?: string | null; interval?: number };
  function setup(raceState: string, extra: Partial<P> = {}, entries: GridEntry[] = grid) {
    return renderHook(
      (p: P) =>
        useAnnouncements({
          raceId: 5,
          settings,
          raceState: p.raceState,
          gridEntries: entries,
          startType: p.startType,
          staggerIntervalSeconds: p.interval,
        }),
      { initialProps: { raceState, startType: 'STAGGER', interval: 2, ...extra } as P },
    );
  }
  const clips = { 'grid-11': 'http://x/11.wav', 'grid-12': 'http://x/12.wav' };

  it('says nothing at the grid call, only once the race starts', () => {
    const { result } = setup('GRID');
    act(() => result.current.setClipMap(clips));
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(audioPlay).not.toHaveBeenCalled();
    expect(speak).not.toHaveBeenCalled();
  });

  it('calls each driver in turn at the set interval after the start', () => {
    const { result, rerender } = setup('GRID');
    act(() => result.current.setClipMap(clips));
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });

    expect(playStartHorn).toHaveBeenCalledTimes(1);
    act(() => { vi.advanceTimersByTime(1199); });
    expect(audioUrls).toHaveLength(0);
    act(() => { vi.advanceTimersByTime(1); });
    expect(audioUrls).toEqual(['http://x/11.wav']);
    act(() => { vi.advanceTimersByTime(1999); });
    expect(audioUrls).toHaveLength(1);
    act(() => { vi.advanceTimersByTime(1); });
    expect(audioUrls).toEqual(['http://x/11.wav', 'http://x/12.wav']);
    expect(speak).not.toHaveBeenCalled();
  });

  it('uses the browser voice when the clips never arrived', () => {
    const { rerender } = setup('GRID');
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(1200 + 2000); });
    expect(speak).toHaveBeenCalledTimes(2);
  });

  it('keeps the scheduled calls when the grid refetches', () => {
    const { rerender } = setup('GRID');
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(1200 + 2000); });
    expect(speak).toHaveBeenCalledTimes(2);
    expect(playStartHorn).toHaveBeenCalledTimes(1);
  });

  it('sounds the horn and says the race has started for a grid start, with no driver calls', () => {
    const { rerender } = setup('GRID', { startType: 'GRID' });
    rerender({ raceState: 'RUNNING', startType: 'GRID', interval: 2 });
    expect(playStartHorn).toHaveBeenCalledTimes(1);
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual(['Race started.']);
  });

  it('stays quiet for a race that was already running', () => {
    setup('RUNNING');
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(speak).not.toHaveBeenCalled();
    expect(playStartHorn).not.toHaveBeenCalled();
  });

  it('skips the horn when it is switched off, and calls the first driver at once', () => {
    const { rerender } = renderHook(
      (p: { raceState: string }) =>
        useAnnouncements({
          raceId: 5, settings: { ...settings, startHorn: false }, raceState: p.raceState,
          gridEntries: grid, startType: 'STAGGER', staggerIntervalSeconds: 2,
        }),
      { initialProps: { raceState: 'GRID' } },
    );
    rerender({ raceState: 'RUNNING' });
    act(() => { vi.advanceTimersByTime(0); });
    expect(playStartHorn).not.toHaveBeenCalled();
    expect(speak).toHaveBeenCalledTimes(1);
  });

  it('says which race is on the grid and how many drivers, once', () => {
    const { rerender } = renderHook(
      (p: { raceState: string }) =>
        useAnnouncements({ raceId: 5, settings, raceState: p.raceState, gridEntries: grid, raceLabel: 'Qualifying 1, Touring, Heat 2' }),
      { initialProps: { raceState: 'PENDING' } },
    );
    rerender({ raceState: 'GRID' });
    rerender({ raceState: 'GRID' });
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual(['Qualifying 1, Touring, Heat 2. 2 drivers to the grid.']);
  });

  it('stays quiet about the grid for a race opened already on it', () => {
    renderHook(() =>
      useAnnouncements({ raceId: 5, settings, raceState: 'GRID', gridEntries: grid, raceLabel: 'Heat 2' }),
    );
    expect(speak).not.toHaveBeenCalled();
  });

  it('calls the drivers when the start comes without the grid being seen', () => {
    const { rerender } = setup('PENDING');
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(1200 + 2000); });
    expect(speak).toHaveBeenCalledTimes(2);
  });

  it('does not call the drivers again when a stopped race resumes', () => {
    const { rerender } = setup('GRID');
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(1200 + 2000); });
    rerender({ raceState: 'STOPPED', startType: 'STAGGER', interval: 2 });
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(10_000); });
    // two calls, then "Race stopped." and "Race resumed."
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual(
      ['Alex Rowe.', 'Sam Ito.', 'Race stopped.', 'Race resumed.']);
    expect(playStartHorn).toHaveBeenCalledTimes(1);
  });

  it('stops calling when the race is stopped', () => {
    const { rerender } = setup('GRID');
    rerender({ raceState: 'RUNNING', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(1200); });
    rerender({ raceState: 'STOPPED', startType: 'STAGGER', interval: 2 });
    act(() => { vi.advanceTimersByTime(10_000); });
    // the first call, then "Race stopped."; the second driver is not called
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual(['Alex Rowe.', 'Race stopped.']);
  });
});

describe('useAnnouncements finish', () => {
  it('plays the race-finished clip', () => {
    const { result, rerender } = renderHook(
      (p: { raceState: string }) => useAnnouncements({ raceId: 5, settings, raceState: p.raceState }),
      { initialProps: { raceState: 'RUNNING' } },
    );
    act(() => result.current.setClipMap({ finish: 'http://x/finish.wav' }));
    rerender({ raceState: 'FINISHED' });
    expect(playFinishHorn).toHaveBeenCalledTimes(1);
    expect(audioUrls).toEqual([]);
    act(() => { vi.advanceTimersByTime(1800); });
    expect(audioUrls).toEqual(['http://x/finish.wav']);
  });

  it('stays quiet for a race opened already finished', () => {
    renderHook(() => useAnnouncements({ raceId: 5, settings, raceState: 'FINISHED' }));
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(playFinishHorn).not.toHaveBeenCalled();
    expect(speak).not.toHaveBeenCalled();
  });

  it('says the race was abandoned, without the horn or the flag', () => {
    const { result, rerender } = renderHook(
      (p: { raceState: string }) => useAnnouncements({ raceId: 5, settings, raceState: p.raceState }),
      { initialProps: { raceState: 'RUNNING' } },
    );
    act(() => result.current.markAbandoned(5));
    rerender({ raceState: 'FINISHED' });
    act(() => { vi.advanceTimersByTime(5000); });
    expect(playFinishHorn).not.toHaveBeenCalled();
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual(['Race abandoned.']);
  });

  it('forgets an abandon the server refused', () => {
    const { result, rerender } = renderHook(
      (p: { raceState: string }) => useAnnouncements({ raceId: 5, settings, raceState: p.raceState }),
      { initialProps: { raceState: 'RUNNING' } },
    );
    act(() => result.current.markAbandoned(5));
    act(() => result.current.clearAbandoned());
    rerender({ raceState: 'FINISHED' });
    expect(playFinishHorn).toHaveBeenCalledTimes(1);
  });

  it('stays quiet about the next race for a finished race that was only opened', () => {
    renderHook(() =>
      useAnnouncements({ raceId: 5, settings, raceState: 'FINISHED', nextRace: { label: 'Final A', marshals: [] } }),
    );
    act(() => { vi.advanceTimersByTime(10_000); });
    expect(speak).not.toHaveBeenCalled();
  });

  it('calls the next race and its marshals after the finish line', () => {
    const { rerender } = renderHook(
      (p: { raceState: string }) =>
        useAnnouncements({
          raceId: 5, settings: { ...settings, finishHorn: false }, raceState: p.raceState,
          nextRace: { label: 'Final A, Touring', marshals: ['Alex Rowe', 'Sam Ito'] },
        }),
      { initialProps: { raceState: 'RUNNING' } },
    );
    rerender({ raceState: 'FINISHED' });
    act(() => { vi.advanceTimersByTime(4000); });
    expect(speak.mock.calls.map((c) => c[0].text)).toEqual([
      'Race finished. Checkered flag.',
      'Next race: Final A, Touring. Marshals: Alex Rowe, Sam Ito.',
    ]);
  });

  it('does not play the previous race\'s clip when the race changes', () => {
    const { result, rerender } = renderHook(
      (p: { raceId: number; raceState: string }) =>
        useAnnouncements({ raceId: p.raceId, settings, raceState: p.raceState }),
      { initialProps: { raceId: 5, raceState: 'RUNNING' } },
    );
    act(() => result.current.setClipMap({ finish: 'http://x/race5-finish.wav' }));
    rerender({ raceId: 6, raceState: 'RUNNING' });
    rerender({ raceId: 6, raceState: 'FINISHED' });
    act(() => { vi.advanceTimersByTime(5000); });
    expect(audioUrls).toEqual([]);
    expect(speak).toHaveBeenCalledTimes(1);
  });
});

describe('useAnnouncements countdown', () => {
  const countdownSettings = { ...settings, countdownIntervals: [10] } as AudioSettingsDto;

  it('says how long is left at the set interval before the race ends', () => {
    renderHook(() =>
      useAnnouncements({
        raceId: 5,
        settings: countdownSettings,
        raceState: 'RUNNING',
        raceEndsAt: Date.now() + 15_000,
      }),
    );

    act(() => { vi.advanceTimersByTime(4_900); });
    expect(speak).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(200); });
    expect(speak).toHaveBeenCalledTimes(1);
    expect(speak.mock.calls[0][0].text).toBe('10 seconds remaining.');
  });

  it('says the time is up when the clock reaches zero', () => {
    renderHook(() =>
      useAnnouncements({
        raceId: 5,
        settings: countdownSettings,
        raceState: 'RUNNING',
        raceEndsAt: Date.now() + 15_000,
      }),
    );

    act(() => { vi.advanceTimersByTime(15_100); });

    expect(speak).toHaveBeenCalledTimes(2);
    expect(speak.mock.calls[1][0].text).toBe('Time. Cars finish as they cross the line.');
  });

  it('stays silent when the race has no end to count down to', () => {
    renderHook(() =>
      useAnnouncements({ raceId: 5, settings: countdownSettings, raceState: 'RUNNING', raceEndsAt: null }),
    );

    act(() => { vi.advanceTimersByTime(60_000); });

    expect(speak).not.toHaveBeenCalled();
  });
});
