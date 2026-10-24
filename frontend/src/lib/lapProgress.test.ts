import { describe, expect, it } from 'vitest';
import { isClose, lapProgress } from './lapProgress';
import type { LiveTimingRowDto } from './raceControlApi';

const row = (over: Partial<LiveTimingRowDto>): LiveTimingRowDto => ({
  entryId: 1, driverName: 'A', carNumber: '1', position: 2, lapsCompleted: 5, lastPassingTimeMs: 100_000,
  lastLapMs: 10_000, bestLapMs: 9_500, avgLapMs: 10_000, overallFastestLapMs: 9_000, lapsDown: 0,
  intervalLapsDown: 0, gapToLeaderMs: 3_000, gapToAheadMs: 800, totalTimeMs: 50_000, ...over,
});

describe('lapProgress', () => {
  it('is green under the average, yellow at it and red a second over', () => {
    expect(lapProgress(row({}), 104_000)).toEqual({ fraction: 0.4, state: 'under' });
    expect(lapProgress(row({}), 110_000)?.state).toBe('at');
    expect(lapProgress(row({}), 110_999)?.state).toBe('at');
    expect(lapProgress(row({}), 111_000)).toEqual({ fraction: 1, state: 'late' });
  });

  it('counts a lap from the resume when the car has not crossed since', () => {
    // Passed the line at 100 s, race stopped, resumed at 140 s: at 144 s the lap is 4 s old, not 44 s
    expect(lapProgress(row({}), 144_000, 140_000)).toEqual({ fraction: 0.4, state: 'under' });
    // A car that crossed after the resume is counted from its own crossing
    expect(lapProgress(row({ lastPassingTimeMs: 142_000 }), 144_000, 140_000)?.fraction).toBe(0.2);
  });

  it('has nothing to show for an average that is zero or not a number', () => {
    expect(lapProgress(row({ avgLapMs: 0 }), 104_000)).toBeNull();
    expect(lapProgress(row({ avgLapMs: Number.NaN }), 104_000)).toBeNull();
  });

  it('has nothing to show before an average, after the flag or with a clock behind the passing', () => {
    expect(lapProgress(row({ avgLapMs: null }), 104_000)).toBeNull();
    expect(lapProgress(row({ finished: true }), 104_000)).toBeNull();
    expect(lapProgress(row({}), 90_000)?.fraction).toBe(0);
  });
});

describe('isClose', () => {
  it('flags a car within the threshold of the one ahead', () => {
    expect(isClose(row({ gapToAheadMs: 800 }), 1000)).toBe(true);
    expect(isClose(row({ gapToAheadMs: 1000 }), 1000)).toBe(true);
    expect(isClose(row({ gapToAheadMs: 1001 }), 1000)).toBe(false);
  });

  it('never flags the leader, a lapped car, a finished car, a missing gap or a switched-off flag', () => {
    expect(isClose(row({ position: 1 }), 1000)).toBe(false);
    expect(isClose(row({ intervalLapsDown: 1 }), 1000)).toBe(false);
    expect(isClose(row({ finished: true }), 1000)).toBe(false);
    expect(isClose(row({ gapToAheadMs: null }), 1000)).toBe(false);
    expect(isClose(row({}), 0)).toBe(false);
  });
});
