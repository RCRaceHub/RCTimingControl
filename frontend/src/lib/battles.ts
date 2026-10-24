/**
 * On-track battles (#505), as the backend works them out and sends them: {@code GET
 * /api/v1/boards/races/{raceId}/battles} and the {@code /topic/race/{raceId}/battles} frames. Nothing here detects
 * a battle; the screens only show what the timing server reports.
 */

/** Which way the gap between two close cars is going, lap on lap. */
export type BattleTrend = 'CLOSING' | 'GROWING' | 'HOLDING' | 'UNKNOWN';

/** A car in a battle. */
export interface BattleCar {
  entryId: number;
  position: number;
  driverName: string;
  carNumber: string | null;
}

/** The gap between a car and the one behind it, in milliseconds; the rate is per lap, negative while closing. */
export interface BattleLink {
  gapMs: number;
  ratePerLapMs: number | null;
  trend: BattleTrend;
  /** Closing by 0.3 s a lap or more. */
  gainingFast: boolean;
}

/** One battle, or a train of three or more cars each close to the next. Links are one fewer than the cars. */
export interface BattleGroup {
  cars: BattleCar[];
  links: BattleLink[];
  gainingFast: boolean;
}

/** The cars gaining fast on the one in front of them, for a table to mark their rows. */
export function gainingFastEntryIds(groups: BattleGroup[]): Set<number> {
  const ids = new Set<number>();
  for (const group of groups) {
    group.links.forEach((link, i) => {
      if (link.gainingFast) ids.add(group.cars[i + 1].entryId);
    });
  }
  return ids;
}
