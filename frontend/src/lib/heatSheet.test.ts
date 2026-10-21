import { describe, it, expect } from 'vitest';
import { averageLapMs, lapSheetColumns, lapTimesFor, positionSeries } from './heatSheet';
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

  it('builds one position line per driver in finishing order, labelled by car, in lap order', () => {
    const history: PositionAtLap[] = [
      { entryId: 2, lapNumber: 2, position: 1, lapTimeMs: 1 },
      { entryId: 1, lapNumber: 1, position: 1, lapTimeMs: 1 },
      { entryId: 2, lapNumber: 1, position: 2, lapTimeMs: 1 },
      { entryId: 1, lapNumber: 2, position: 2, lapTimeMs: 1 },
    ];

    const series = positionSeries([row(2, 1), row(1, 2)], history);

    expect(series.map((s) => s.label)).toEqual(['Car 2', 'Car 1']);
    expect(series[0].points).toEqual([{ lap: 1, position: 2 }, { lap: 2, position: 1 }]);
  });

  it('leaves a driver with no recorded laps out of the position chart', () => {
    expect(positionSeries([row(1, 1)], [])).toEqual([]);
  });

  it('has no chart when no driver ever changed place', () => {
    const flat: PositionAtLap[] = [lap(1, 1, 1), lap(1, 2, 1), lap(2, 1, 1), lap(2, 2, 1)].map((l) => ({ ...l, position: l.entryId }));

    expect(positionSeries([row(1, 1), row(2, 2)], flat)).toEqual([]);
  });
});
