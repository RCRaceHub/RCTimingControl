/** One car's sector times in milliseconds, one entry per sector in track order (#676). */
export type SectorRow = {
  entryId: number;
  lapNumber: number;
  /** The car's latest lap that had every split. */
  lastMs: number[];
  /** The car's fastest time in each sector so far. */
  bestMs: number[];
};

/**
 * A race's sector times. {@code sectorCount} is how many sectors a lap has: 0 when the club has no split decoders, so
 * a page shows no sector columns at all.
 */
export type SectorBoard = {
  sectorCount: number;
  /** The fastest anyone has gone in each sector so far; entries are null until someone has a time. */
  overallBestMs: (number | null)[];
  rows: SectorRow[];
};

/** No split decoders, and nothing seen. */
export const NO_SECTORS: SectorBoard = { sectorCount: 0, overallBestMs: [], rows: [] };

/** How a sector time reads against the bests: the fastest of anyone, the car's own best, or neither. */
export type SectorTone = 'overall-best' | 'personal-best' | 'normal';

/** Colours one sector time of a car's latest lap against the car's and the race's bests. */
export function sectorTone(row: SectorRow, board: SectorBoard, sector: number): SectorTone {
  const value = row.lastMs[sector];
  if (value === undefined) return 'normal';
  if (board.overallBestMs[sector] === value) return 'overall-best';
  if (row.bestMs[sector] === value) return 'personal-best';
  return 'normal';
}

/** One car's sector times in a finished race: its best in each sector (#676). */
export type DriverSectorResults = {
  entryId: number;
  bestMs: number[];
  laps: { lapNumber: number; sectorMs: number[] }[];
};

/** A finished race's saved sector times. {@code sectorCount} is 0 when the race has none, so a printout shows no sector section. */
export type SectorResults = {
  sectorCount: number;
  overallBestMs: (number | null)[];
  drivers: DriverSectorResults[];
};

/** No saved sector times. */
export const NO_SECTOR_RESULTS: SectorResults = { sectorCount: 0, overallBestMs: [], drivers: [] };
