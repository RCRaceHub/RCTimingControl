import { describe, expect, it } from 'vitest';
import { everyoneOffLapZero, holdBackLapped, isClose, lapProgress, onPace, placesGained, predictedLaps } from './lapProgress';
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

  it('waits until the car has had a lap counted', () => {
    expect(isClose(row({ lapsCompleted: 0, gapToAheadMs: 300 }), 1000)).toBe(false);
    expect(isClose(row({ lapsCompleted: 1, gapToAheadMs: 300 }), 1000)).toBe(true);
  });
});

describe('placesGained', () => {
  it('is positive up the order, negative down it and null with no grid place', () => {
    const rows = [row({ entryId: 1, position: 1, startPosition: 3 }), row({ entryId: 2, position: 2, startPosition: 2 }), row({ entryId: 3, position: 3, startPosition: 1 })];
    expect(placesGained(rows[0], rows)).toBe(2);
    expect(placesGained(rows[1], rows)).toBe(0);
    expect(placesGained(rows[2], rows)).toBe(-2);
    expect(placesGained(row({ startPosition: null }), rows)).toBeNull();
    expect(placesGained(row({}), rows)).toBeNull();
  });

  it('ranks the grid among the cars that have crossed, not the whole field', () => {
    // The pole sitter has not crossed yet, so the car from grid 5 that leads is not "up four"
    const rows = [row({ entryId: 1, position: 1, startPosition: 5 }), row({ entryId: 2, position: 2, startPosition: 6 })];
    expect(placesGained(rows[0], rows)).toBe(0);
    expect(placesGained(rows[1], rows)).toBe(0);
  });
});

describe('predictedLaps', () => {
  it('counts the crossings in the time left and the one that ends the race', () => {
    // Crossed at 100 s, 10 s laps, 25 s to go: crossings at 110 and 120 are inside, 130 ends it => 3 more laps
    expect(predictedLaps(row({}), 100_000, 25_000)).toBe(5 + 3);
    // 4 s after the crossing the same race has 21 s left: 4 + 21 = 25 s still
    expect(predictedLaps(row({}), 104_000, 21_000)).toBe(5 + 3);
    // A crossing exactly at the end counts as the one that ends it
    expect(predictedLaps(row({}), 100_000, 20_000)).toBe(5 + 3);
  });

  it('always counts the lap that ends the race', () => {
    expect(predictedLaps(row({}), 100_000, 1)).toBe(5 + 1);
  });

  it('counts the lap from the resume when the car has not crossed since a stoppage', () => {
    // Crossed at 100 s, stopped, resumed at 140 s with 25 s left: at 144 s the lap is 4 s old, not 44 s
    expect(predictedLaps(row({}), 144_000, 25_000, 140_000)).toBe(5 + 3);
  });

  it('is null with no average, no time limit, no time left or a finished car', () => {
    expect(predictedLaps(row({ avgLapMs: null }), 100_000, 25_000)).toBeNull();
    expect(predictedLaps(row({}), 100_000, null)).toBeNull();
    expect(predictedLaps(row({}), 100_000, 0)).toBeNull();
    expect(predictedLaps(row({ finished: true }), 100_000, 25_000)).toBeNull();
  });
});

describe('holdBackLapped', () => {
  const lapped = row({ entryId: 2, lapsCompleted: 1, lapsDown: 1, intervalLapsDown: 1 });
  const onLapZero = row({ entryId: 3, lapsCompleted: 0 });

  it('clears laps down while any car is still on lap 0', () => {
    const rows = holdBackLapped([row({ entryId: 1, position: 1, lapsCompleted: 2 }), lapped, onLapZero]);
    expect(rows[1].lapsDown).toBe(0);
    expect(rows[1].intervalLapsDown).toBe(0);
    expect(everyoneOffLapZero([row({ lapsCompleted: 0 }), row({ lapsCompleted: 1 })])).toBe(false);
  });

  it('leaves the rows alone once every car has had a lap counted', () => {
    const rows = [row({ entryId: 1, position: 1, lapsCompleted: 2 }), lapped];
    expect(holdBackLapped(rows)).toBe(rows);
    expect(rows[1].lapsDown).toBe(1);
  });
});

describe('onPace', () => {
  it('is on pace while the last lap was no slower than the average and the current lap is not late', () => {
    expect(onPace(row({ lastLapMs: 10_000 }), 104_000)).toBe(true);
    expect(onPace(row({ lastLapMs: 9_000 }), 110_999)).toBe(true);
  });

  it('is off pace once the current lap runs late', () => {
    expect(onPace(row({}), 111_000)).toBe(false);
  });

  it('is off pace when the last lap was slower than the average', () => {
    expect(onPace(row({ lastLapMs: 10_001 }), 104_000)).toBe(false);
  });

  it('is on pace with no last lap to judge, and off with no average or after the flag', () => {
    expect(onPace(row({ lastLapMs: null }), 104_000)).toBe(true);
    expect(onPace(row({ avgLapMs: null }), 104_000)).toBe(false);
    expect(onPace(row({ finished: true }), 104_000)).toBe(false);
  });

  it('does not count a stoppage against the lap', () => {
    expect(onPace(row({}), 144_000, 140_000)).toBe(true);
  });
});
