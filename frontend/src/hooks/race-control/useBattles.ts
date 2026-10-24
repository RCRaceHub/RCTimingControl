import { useState } from 'react';
import { advanceBattles, type Battle, type BattleHistory } from '@/lib/battles';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * The on-track battles in a race (#505), judged afresh with each timing update. Changing race, or the threshold,
 * starts the history again, so a trend never carries over from another race. {@code rows} must be the same array
 * between updates (not rebuilt on each render), or this re-renders without end.
 */
export function useBattles(raceId: number | null, rows: LiveTimingRowDto[], thresholdMs: number): Battle[] {
  const [seen, setSeen] = useState<{
    raceId: number | null;
    rows: LiveTimingRowDto[] | null;
    thresholdMs: number;
    history: BattleHistory;
    battles: Battle[];
  }>({ raceId, rows: null, thresholdMs, history: new Map(), battles: [] });

  // Adjusted while rendering rather than in an effect, so the battles belong to the rows being shown
  if (seen.raceId !== raceId || seen.thresholdMs !== thresholdMs || seen.rows !== rows) {
    const fresh = seen.raceId !== raceId || seen.thresholdMs !== thresholdMs;
    const advanced = advanceBattles(fresh ? new Map() : seen.history, rows, thresholdMs);
    setSeen({ raceId, rows, thresholdMs, ...advanced });
    return advanced.battles;
  }
  return seen.battles;
}
