import { predictedLaps } from '@/lib/lapProgress';
import { useTick } from '@/lib/tick';
import type { LapBarClock } from '@/components/LapProgressBar';
import type { LiveQualifyingRow } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { tqPaceText } from '@/pages/boards/boardFormat';

/**
 * A line comparing the running heat's leader with the event's top qualifier (#531). It re-renders by itself four
 * times a second so a timed heat's predicted laps stay current, like {@link PredictedLaps}; draws nothing until
 * there is something to compare.
 *
 * @param remainingAt the race time left at a given device time in milliseconds, or null when there is no time limit
 */
export function TqPace({ leader, tq, clock, remainingAt, className }: {
  leader: LiveTimingRowDto | undefined;
  tq: LiveQualifyingRow | undefined;
  clock: LapBarClock;
  remainingAt: (deviceNowMs: number) => number | null;
  className?: string;
}) {
  const deviceNow = useTick();
  const predicted = leader
    ? predictedLaps(leader, deviceNow + clock.offsetMs, remainingAt(deviceNow), clock.resumedAtMs)
    : null;
  const text = tqPaceText(leader, tq, predicted);
  return text ? <div className={className} aria-label="Pace against the top qualifier">{text}</div> : null;
}
