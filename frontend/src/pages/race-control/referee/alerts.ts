/** How much a car's gap to the car ahead must shrink between two updates to count as closing. */
export const PROXIMITY_CLOSING_DELTA_MS = 500;
/** A closing car is only flagged once it is within this gap of the car ahead. */
export const PROXIMITY_ABSOLUTE_MAX_MS = 3000;

export interface ProximityRow {
  entryId: number;
  gapToAheadMs: number | null;
}

/**
 * OFFICIAL-01: entryIds whose gapToAheadMs has shrunk by >= PROXIMITY_CLOSING_DELTA_MS since previous and is now
 * within PROXIMITY_ABSOLUTE_MAX_MS. Pure: it compares two snapshots and holds no state. With no previous snapshot, or
 * for an entry missing from it, or a null or zero gap on either side, nothing is flagged.
 */
export function computeProximityAlerts(
  current: ProximityRow[],
  previous: ProximityRow[] | null,
): Set<number> {
  if (!previous || previous.length === 0) return new Set<number>();

  const prevById = new Map(previous.map((p) => [p.entryId, p]));
  const result = new Set<number>();

  for (const c of current) {
    const p = prevById.get(c.entryId);
    if (!p) continue;
    if (
      c.gapToAheadMs !== null &&
      p.gapToAheadMs !== null &&
      c.gapToAheadMs !== 0 &&
      p.gapToAheadMs !== 0 &&
      p.gapToAheadMs > c.gapToAheadMs + PROXIMITY_CLOSING_DELTA_MS &&
      c.gapToAheadMs <= PROXIMITY_ABSOLUTE_MAX_MS
    ) {
      result.add(c.entryId);
    }
  }

  return result;
}
