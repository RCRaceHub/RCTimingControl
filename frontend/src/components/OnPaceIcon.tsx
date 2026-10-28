import { onPace } from '@/lib/lapProgress';
import { useTick } from '@/lib/tick';
import type { LapBarClock } from '@/components/LapProgressBar';
import { StateIcon } from '@/components/StateIcon';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * A thumbs up beside a driver's name while the car is on pace to make the laps {@code PredictedLaps} shows (#479):
 * its last lap no slower than its average and the current lap not yet running late. It reads the same clock as
 * the lap bar and re-renders by itself four times a second, so the table around it does not.
 */
export function OnPaceIcon({ row, clock, className }: { row: LiveTimingRowDto; clock: LapBarClock; className?: string }) {
  const nowMs = useTick() + clock.offsetMs;
  if (!onPace(row, nowMs, clock.resumedAtMs, clock.lateMarginMs)) return null;
  return <StateIcon kind="on-pace" className={className} />;
}
