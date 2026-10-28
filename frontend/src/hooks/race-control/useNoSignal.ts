import { useEffect, useRef, useState } from 'react';
import type { LapBarClock } from '@/components/LapProgressBar';
import { signalLost } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How often the silence of each car is checked; a silence is counted in laps, so a second is fine. */
const CHECK_MS = 1000;

const NONE: ReadonlySet<number> = new Set();

/**
 * The cars the decoder has not seen for a while (#591), by entry id. Checked once a second and the set replaced only
 * when it changes, so a board re-renders when a car goes quiet or comes back, not on every check.
 *
 * <p>Empty without a clock, which is how a board says the race is not running, and until the clock's offset has been
 * learned from a fresh passing (a device clock that is ahead of the timing laptop would otherwise flag every car).
 * The first check comes one interval after the clock appears, not at once, so a race that has just resumed has had
 * its resume time set before any car is judged against the stoppage.
 *
 * @param clock how to read the timing laptop's clock; omitted to turn the check off
 */
export function useNoSignal(rows: LiveTimingRowDto[], clock?: LapBarClock): ReadonlySet<number> {
  const [lost, setLost] = useState<ReadonlySet<number>>(NONE);
  const latest = useRef({ rows, clock });
  useEffect(() => {
    latest.current = { rows, clock };
  });
  const running = clock !== undefined;
  useEffect(() => {
    if (!running) return;
    const timer = setInterval(() => {
      const { rows: current, clock: c } = latest.current;
      const next = new Set<number>();
      if (c?.offsetKnown) {
        const nowMs = Date.now() + c.offsetMs;
        for (const row of current) if (signalLost(row, nowMs, c.resumedAtMs)) next.add(row.entryId);
      }
      setLost((prev) => (prev.size === next.size && [...next].every((id) => prev.has(id)) ? prev : next));
    }, CHECK_MS);
    return () => {
      clearInterval(timer);
      setLost(NONE);
    };
  }, [running]);
  return running ? lost : NONE;
}
