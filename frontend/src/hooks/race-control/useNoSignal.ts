import { useEffect, useState } from 'react';
import type { LapBarClock } from '@/components/LapProgressBar';
import { signalLost } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How often the silence of each car is checked; a silence is counted in laps, so a second is fine. */
const CHECK_MS = 1000;

/**
 * The cars the decoder has not seen for a while (#591), by entry id. Checked once a second and the set replaced only
 * when it changes, so a board re-renders when a car goes quiet or comes back, not on every check. Empty without a
 * clock, which is how a board says the race is not running.
 *
 * @param clock how to read the timing laptop's clock; omitted to turn the check off
 */
export function useNoSignal(rows: LiveTimingRowDto[], clock?: LapBarClock): ReadonlySet<number> {
  const [lost, setLost] = useState<ReadonlySet<number>>(() => new Set());
  useEffect(() => {
    const check = () => {
      const next = new Set<number>();
      if (clock) {
        const nowMs = Date.now() + clock.offsetMs;
        for (const row of rows) if (signalLost(row, nowMs, clock.resumedAtMs)) next.add(row.entryId);
      }
      setLost((prev) => (prev.size === next.size && [...next].every((id) => prev.has(id)) ? prev : next));
    };
    check();
    const timer = setInterval(check, CHECK_MS);
    return () => clearInterval(timer);
  }, [rows, clock]);
  return lost;
}
