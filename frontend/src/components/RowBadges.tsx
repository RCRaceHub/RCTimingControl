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
      className={cn('shrink-0 rounded-full border border-info px-1.5 text-[0.7em] font-bold leading-tight text-info', className)}
    >
      +{row.lapsDown}
    </span>
  );
}

/**
 * A flame beside a driver's name while the car's latest lap is a flying lap: quicker than the average of the laps
 * before it, with at least three to beat and the first lap left out (the rule the overlay ticker uses). The server
 * works it out per lap, so the flame shows from the lap it was set until the car's next crossing, and goes if that
 * lap is not flying too. Nothing once the car has finished.
 */
export function FlyingLapIcon({ row, className }: { row: LiveTimingRowDto; className?: string }) {
  if (!row.flyingLap || row.finished) return null;
  return <StateIcon kind="flying" className={className} />;
}

/** Whether the referee has added time to a car, for the penalty marker. */
function hasTimePenalty(row: LiveTimingRowDto): boolean {
  return (row.timePenaltyMs ?? 0) > 0;
}

/** The flying lap, lapped, penalty and no-signal markers (#591) that go beside a driver's name; any, all or none. */
export function RowMarkers({ row, noSignal, className }: { row: LiveTimingRowDto; noSignal: boolean; className?: string }) {
  return (
    <>
      <FlyingLapIcon row={row} className={className} />
      <LappedPill row={row} className={className} />
      {hasTimePenalty(row) && <StateIcon kind="penalty" className={className} />}
      {noSignal && <StateIcon kind="no-signal" className={className} />}
    </>
  );
}
