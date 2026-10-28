import { StateIcon } from '@/components/StateIcon';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { cn } from '@/lib/utils';

/**
 * A "+N" pill for a car N laps down (#591), so being lapped reads from the shape and the number, not only from the
 * gap column's text. Nothing for a car on the lead lap.
 */
export function LappedPill({ row, className }: { row: LiveTimingRowDto; className?: string }) {
  if (row.lapsDown <= 0) return null;
  const label = `${row.lapsDown} ${row.lapsDown === 1 ? 'lap' : 'laps'} down`;
  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      data-state="lapped"
      className={cn('shrink-0 rounded-full border border-sky-400 px-1.5 text-[0.7em] font-bold leading-tight text-sky-400', className)}
    >
      +{row.lapsDown}
    </span>
  );
}

/** Whether the referee has added time to a car, for the penalty marker. */
function hasTimePenalty(row: LiveTimingRowDto): boolean {
  return (row.timePenaltyMs ?? 0) > 0;
}

/** The penalty marker and the no-signal marker (#591) that go beside a driver's name; either, both or neither. */
export function RowMarkers({ row, noSignal, className }: { row: LiveTimingRowDto; noSignal: boolean; className?: string }) {
  return (
    <>
      <LappedPill row={row} className={className} />
      {hasTimePenalty(row) && <StateIcon kind="penalty" className={className} />}
      {noSignal && <StateIcon kind="no-signal" className={className} />}
    </>
  );
}
