import { lapProgress } from '@/lib/lapProgress';
import { useTick } from '@/lib/tick';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { cn } from '@/lib/utils';

const COLOURS = { under: 'bg-green-500', at: 'bg-yellow-400', late: 'bg-red-500' } as const;

/** How the bars read the clock: the timing laptop's clock less this device's, and when the race last resumed. */
export interface LapBarClock {
  offsetMs: number;
  /** In the timing laptop's clock; null when the race has not been stopped and resumed. */
  resumedAtMs: number | null;
  /** How far past its average a lap runs before the bar turns red, in milliseconds: the club's setting. */
  lateMarginMs?: number;
}

/**
 * A thin bar for how far the car is round its lap against its average (#479): green while under the average,
 * yellow at it, red when a second over. Empty space when the car has no average yet. It re-renders by itself
 * four times a second, so the table around it does not.
 */
export function LapProgressBar({ row, clock, className }: { row: LiveTimingRowDto; clock: LapBarClock; className?: string }) {
  const nowMs = useTick() + clock.offsetMs;
  const progress = lapProgress(row, nowMs, clock.resumedAtMs, clock.lateMarginMs);
  return (
    <div className={cn('h-2 w-full overflow-hidden rounded bg-slate-700/60', className)} aria-hidden="true">
      {progress && (
        <div
          data-testid="lap-progress"
          data-state={progress.state}
          className={cn('h-full', COLOURS[progress.state])}
          style={{ width: `${Math.round(progress.fraction * 100)}%` }}
        />
      )}
    </div>
  );
}
