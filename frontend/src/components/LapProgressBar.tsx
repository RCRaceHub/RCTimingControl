import { lapProgress } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { cn } from '@/lib/utils';

const COLOURS = { under: 'bg-green-500', at: 'bg-yellow-400', late: 'bg-red-500' } as const;

/**
 * A thin bar for how far the car is round its lap against its average (#479): green while under the average,
 * yellow at it, red when a second over. Empty space when the car has no average yet.
 */
export function LapProgressBar({ row, nowMs, className }: { row: LiveTimingRowDto; nowMs: number; className?: string }) {
  const progress = lapProgress(row, nowMs);
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
