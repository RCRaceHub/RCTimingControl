import { describe, it, expect } from 'vitest';
import { isImprovingLap, lapPace, raceEndsAt } from './raceAudioCues';

describe('raceEndsAt', () => {
  it('counts the remaining time from when the clock was read', () => {
    expect(raceEndsAt({ running: true, remainingMs: 90_000 }, 1_000_000)).toBe(1_090_000);
  });

  it('has no end for a race with no set length, a stopped clock or no clock yet', () => {
    expect(raceEndsAt({ running: true, remainingMs: null }, 1_000_000)).toBeNull();
    expect(raceEndsAt({ running: false, remainingMs: 90_000 }, 1_000_000)).toBeNull();
    expect(raceEndsAt(undefined, 1_000_000)).toBeNull();
  });
});

describe('isImprovingLap', () => {
  it('is true when the driver\'s best came down on this lap', () => {
    expect(isImprovingLap(21_500, { bestLapMs: 21_100 })).toBe(true);
  });

  it('is false when the best did not change, or the driver has no best yet', () => {
    expect(isImprovingLap(21_100, { bestLapMs: 21_100 })).toBe(false);
    expect(isImprovingLap(21_100, { bestLapMs: 22_000 })).toBe(false);
    expect(isImprovingLap(undefined, { bestLapMs: 21_100 })).toBe(false);
    expect(isImprovingLap(21_100, { bestLapMs: null })).toBe(false);
  });
});

describe('lapPace', () => {
  it('is faster for a new best', () => {
    expect(lapPace(21_500, { bestLapMs: 21_100, lastLapMs: 21_100 })).toBe('faster');
  });

  it('is on pace within 2% of the best, and when there is nothing to compare with', () => {
    expect(lapPace(21_000, { bestLapMs: 21_000, lastLapMs: 21_400 })).toBe('onPace');
    expect(lapPace(undefined, { bestLapMs: 21_000, lastLapMs: 21_000 })).toBe('onPace');
  });

  it('is slower beyond that', () => {
    expect(lapPace(21_000, { bestLapMs: 21_000, lastLapMs: 22_000 })).toBe('slower');
  });
});
