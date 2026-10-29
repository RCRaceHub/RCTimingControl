import { paceState } from '@/lib/lapProgress';
import { useTick } from '@/lib/tick';
import type { LapBarClock } from '@/components/LapProgressBar';
import { useRowIcon } from '@/components/RowIconsContext';
import { StateIcon } from '@/components/StateIcon';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * A thumbs up beside a driver's name while the car is on pace to make the laps {@code PredictedLaps} shows (#479),
 * and a thumbs down once it is off pace: its last lap slower than its average, or the current lap running late.
 * Nothing shows for a car with no average yet. It reads the same clock as the lap bar and re-renders by itself
 * four times a second, so the table around it does not.
 */
export function PaceIcon({ row, clock, className }: { row: LiveTimingRowDto; clock: LapBarClock; className?: string }) {
  const shown = useRowIcon('pace');
  const nowMs = useTick() + clock.offsetMs;
  const pace = paceState(row, nowMs, clock.resumedAtMs, clock.lateMarginMs);
  if (!shown || !pace) return null;
  return <StateIcon kind={pace === 'on' ? 'on-pace' : 'off-pace'} className={className} />;
}
