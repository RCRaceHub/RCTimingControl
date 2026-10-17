import type { PositionAtLap, ResultRow } from './raceControlApi';

/** Every timed lap of one driver, in lap order. */
export function lapTimesFor(entryId: number, lapHistory: PositionAtLap[]): number[] {
  return lapHistory
    .filter((l) => l.entryId === entryId && l.lapTimeMs != null && l.lapTimeMs > 0)
    .sort((a, b) => a.lapNumber - b.lapNumber)
    .map((l) => l.lapTimeMs as number);
}

/** The mean of a driver's timed laps; null when they have none. */
export function averageLapMs(laps: number[]): number | null {
  if (laps.length === 0) return null;
  return Math.round(laps.reduce((sum, ms) => sum + ms, 0) / laps.length);
}

export type LapSheetColumn = {
  entryId: number;
  driverName: string;
  carNumber: string | null;
  laps: number[];
  /** Index into `laps` of the driver's quickest lap, or -1 when none. */
  fastestIndex: number;
};

/** One column per driver in finishing order, for the lap-by-lap sheet. */
export function lapSheetColumns(positions: ResultRow[], lapHistory: PositionAtLap[]): LapSheetColumn[] {
  return [...positions]
    .sort((a, b) => a.position - b.position)
    .map((row) => {
      const laps = lapTimesFor(row.entryId, lapHistory);
      const fastest = laps.length > 0 ? Math.min(...laps) : -1;
      return {
        entryId: row.entryId,
        driverName: row.driverName,
        carNumber: row.carNumber,
        laps,
        fastestIndex: fastest > 0 ? laps.indexOf(fastest) : -1,
      };
    })
    .filter((col) => col.laps.length > 0);
}
