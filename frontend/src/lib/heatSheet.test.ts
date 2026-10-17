import { describe, it, expect } from 'vitest';
import { averageLapMs, lapSheetColumns, lapTimesFor } from './heatSheet';
import type { PositionAtLap, ResultRow } from './raceControlApi';

const lap = (entryId: number, lapNumber: number, lapTimeMs: number | null): PositionAtLap => ({
  entryId,
  lapNumber,
  position: 1,
  lapTimeMs,
});

const row = (position: number, entryId: number): ResultRow => ({
  position,
  entryId,
  driverName: `Driver ${entryId}`,
  carNumber: String(entryId),
  lapsCompleted: 3,
  totalTimeMs: 0,
  bestLapMs: null,
  gapToLeaderMs: null,
});

describe('heatSheet', () => {
  it("collects a driver's timed laps in lap order and skips laps without a time", () => {
    const history = [lap(1, 2, 20_000), lap(2, 1, 9_000), lap(1, 1, 21_000), lap(1, 3, null)];

    expect(lapTimesFor(1, history)).toEqual([21_000, 20_000]);
  });

  it('averages the timed laps, and has no average without any', () => {
    expect(averageLapMs([20_000, 21_000, 22_000])).toBe(21_000);
    expect(averageLapMs([])).toBeNull();
  });

  it("lays the sheet out by finishing order and marks each driver's quickest lap", () => {
    const history = [lap(2, 1, 22_000), lap(2, 2, 20_500), lap(1, 1, 21_000), lap(1, 2, 21_500)];

    const columns = lapSheetColumns([row(2, 1), row(1, 2)], history);

    expect(columns.map((c) => c.entryId)).toEqual([2, 1]);
    expect(columns[0].fastestLap).toBe(2);
    expect(columns[1].fastestLap).toBe(1);
  });

  it('keeps the real lap numbers when a lap has no time', () => {
    const history = [lap(1, 1, 21_000), lap(1, 2, null), lap(1, 3, 20_000)];

    const [column] = lapSheetColumns([row(1, 1)], history);

    expect(column.laps.map((l) => l.lapNumber)).toEqual([1, 3]);
    expect(column.fastestLap).toBe(3);
  });

  it('leaves out a driver with no timed lap', () => {
    expect(lapSheetColumns([row(1, 5)], [])).toEqual([]);
  });
});
