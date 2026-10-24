import { predictedLaps } from '@/lib/lapProgress';
import { useTick } from '@/lib/tick';
import type { LapBarClock } from '@/components/LapProgressBar';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * The laps a car is on course to finish a timed race with (#479), from its average lap and the time left. It
 * re-renders by itself four times a second like the lap bars, so the table around it does not.
 *
 * @param remainingAt the race time left at a given device time in milliseconds, or null when there is no time limit
 *     or the clock is not counting, which reads as a dash
 */
export function PredictedLaps({ row, clock, remainingAt }: {
  row: LiveTimingRowDto;
  clock: LapBarClock;
  remainingAt: (deviceNowMs: number) => number | null;
}) {
  const deviceNow = useTick();
  const laps = predictedLaps(row, deviceNow + clock.offsetMs, remainingAt(deviceNow), clock.resumedAtMs);
  return <>{laps ?? '—'}</>;
}
