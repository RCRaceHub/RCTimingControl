import { describe, expect, it } from 'vitest';
import { ClockOffsetEstimator } from './clockOffset';
import type { LiveTimingRowDto } from './raceControlApi';

const row = (entryId: number, lastPassingTimeMs: number) => ({ entryId, lastPassingTimeMs }) as LiveTimingRowDto;

describe('ClockOffsetEstimator', () => {
  it('knows nothing from the rows a board first loads, however old', () => {
    const estimator = new ClockOffsetEstimator();
    expect(estimator.observe([row(1, 1_000), row(2, 2_000)], 500_000)).toBe(0);
  });

  it('takes the offset from fresh passings, trusting the one that arrived quickest', () => {
    const estimator = new ClockOffsetEstimator();
    estimator.observe([row(1, 1_000)], 100_000);
    // This device is 5 s behind the laptop: a passing stamped 200 000 reaches it at local 195 100 (100 ms late)
    expect(estimator.observe([row(1, 200_000)], 195_100)).toBe(4_900);
    // A later passing that took 400 ms to arrive does not pull the estimate down
    expect(estimator.observe([row(1, 210_000)], 205_400)).toBe(4_900);
    // One that arrived straight away raises it
    expect(estimator.observe([row(1, 220_000)], 214_950)).toBe(5_050);
  });

  it('ignores a row whose passing has not changed', () => {
    const estimator = new ClockOffsetEstimator();
    estimator.observe([row(1, 1_000)], 100_000);
    expect(estimator.observe([row(1, 1_000)], 300_000)).toBe(0);
  });
});
