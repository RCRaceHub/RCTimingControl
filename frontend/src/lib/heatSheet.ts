import type { PositionAtLap, ResultRow } from './raceControlApi';

export type TimedLap = { lapNumber: number; ms: number };

/** Every timed lap of one driver, in lap order. A lap without a time is left out, so lap numbers can skip. */
export function timedLapsFor(entryId: number, lapHistory: PositionAtLap[]): TimedLap[] {
  return lapHistory
    .filter((l) => l.entryId === entryId && l.lapTimeMs != null && l.lapTimeMs > 0)
    .sort((a, b) => a.lapNumber - b.lapNumber)
    .map((l) => ({ lapNumber: l.lapNumber, ms: l.lapTimeMs as number }));
}

/** The lap times alone, in lap order. */
export function lapTimesFor(entryId: number, lapHistory: PositionAtLap[]): number[] {
  return timedLapsFor(entryId, lapHistory).map((l) => l.ms);
}

/** The mean of a driver's timed laps (not of every lap, so it needn't match total time over laps); null when none. */
export function averageLapMs(laps: number[]): number | null {
  if (laps.length === 0) return null;
  return Math.round(laps.reduce((sum, ms) => sum + ms, 0) / laps.length);
}

export type LapSheetColumn = {
  entryId: number;
  driverName: string;
  carNumber: string | null;
  laps: TimedLap[];
  /** Lap number of the driver's quickest lap, or null when none. */
  fastestLap: number | null;
};

/** One column per driver in finishing order, for the lap-by-lap sheet. */
export function lapSheetColumns(positions: ResultRow[], lapHistory: PositionAtLap[]): LapSheetColumn[] {
  return [...positions]
    .sort((a, b) => a.position - b.position)
    .map((row) => {
      const laps = timedLapsFor(row.entryId, lapHistory);
      const fastest = laps.reduce<TimedLap | null>((best, l) => (best === null || l.ms < best.ms ? l : best), null);
      return {
        entryId: row.entryId,
        driverName: row.driverName,
        carNumber: row.carNumber,
        laps,
        fastestLap: fastest?.lapNumber ?? null,
      };
    })
    .filter((col) => col.laps.length > 0);
}
