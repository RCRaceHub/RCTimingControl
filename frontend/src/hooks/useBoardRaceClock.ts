import { useEffect, useState } from 'react';
import type { RaceClockDto } from '@/lib/boardsApi';
import { useForcedCloseQuery } from '@/hooks/useBoardQueries';
import { fmtClock } from '@/pages/boards/boardFormat';

/** How often the forced-close answer is re-read once the race time is up; a lap that counts puts the wait back. */
const FORCED_CLOSE_POLL_MS = 3000;

/**
 * What a board's race clock reads: the time left in a timed race, the time so far in one with no length, and once a
 * running timed race is past its time the time left for the cars to finish (the track's max last lap or the format's
 * max race window, whichever comes first), flagged by {@code overrun} so the board can colour it. Counts on between
 * the polled clock and forced-close answers, and reads the forced-close answer only while overrun.
 *
 * @param clock       the last race clock answer, undefined until one arrives
 * @param clockReadAt when this device received it, in milliseconds
 * @return {@code text} as m:ss, null before a clock has arrived; {@code overrun} true while counting down the finish
 */
export function useBoardRaceClock(raceId: number | null, clock: RaceClockDto | undefined, clockReadAt: number) {
  const [now, setNow] = useState(() => Date.now());
  const running = clock?.running === true;
  useEffect(() => {
    if (!running) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [running]);

  const sinceRead = running ? Math.max(0, now - clockReadAt) : 0;
  const overrun = running && clock?.remainingMs != null && clock.remainingMs - sinceRead <= 0;
  const { data: forcedClose, dataUpdatedAt: forcedCloseReadAt } = useForcedCloseQuery(raceId, {
    intervalMs: FORCED_CLOSE_POLL_MS,
    enabled: overrun,
  });

  if (!clock) return { text: null, overrun: false };
  if (overrun) {
    const left = forcedClose?.closesInMs == null ? 0 : forcedClose.closesInMs - Math.max(0, now - forcedCloseReadAt);
    return { text: fmtClock(left), overrun: true };
  }
  const elapsed = clock.elapsedMs + sinceRead;
  return { text: clock.durationMs != null ? fmtClock(clock.durationMs - elapsed) : fmtClock(elapsed), overrun: false };
}
