import { Fragment, type ReactNode } from 'react';
import type { BattleCar, BattleGroup, BattleLink } from '@/lib/battles';
import { cn } from '@/lib/utils';

/** The gap as seconds to one decimal place, e.g. "0.8s". */
function fmtGap(ms: number): string {
  return `${(ms / 1000).toFixed(1)}s`;
}

const TREND_TEXT = {
  CLOSING: { arrow: '▲', label: 'closing', className: 'text-success' },
  GROWING: { arrow: '▼', label: 'pulling away', className: 'text-destructive' },
  HOLDING: { arrow: '▬', label: 'holding', className: 'text-muted-foreground' },
} as const;

/** What one link of a battle says in words, for the screen reader and the hover text. */
function describe(ahead: BattleCar, chaser: BattleCar, link: BattleLink): string {
  const gap = fmtGap(link.gapMs);
  if (link.trend === 'UNKNOWN' || link.ratePerLapMs === null) return `${chaser.driverName} is ${gap} behind ${ahead.driverName}`;
  const rate = `${fmtGap(Math.abs(link.ratePerLapMs))} a lap`;
  return `${chaser.driverName} is ${gap} behind ${ahead.driverName}, ${TREND_TEXT[link.trend].label}${link.trend === 'HOLDING' ? '' : ` by ${rate}`}`;
}

/** The gap between two cars with an arrow for whether it is closing or growing (#505). */
function GapLink({ ahead, chaser, link }: { ahead: BattleCar; chaser: BattleCar; link: BattleLink }) {
  const trend = link.trend === 'UNKNOWN' ? null : TREND_TEXT[link.trend];
  return (
    <span
      className={cn('whitespace-nowrap font-semibold tabular-nums', trend?.className, link.gainingFast && 'underline')}
      data-trend={link.trend}
      data-gaining-fast={link.gainingFast || undefined}
      title={describe(ahead, chaser, link)}
    >
      <span className="sr-only">{describe(ahead, chaser, link)}</span>
      <span aria-hidden="true">{fmtGap(link.gapMs)}</span>
      {trend && ` ${trend.arrow}`}
      {trend && link.ratePerLapMs !== null && link.trend !== 'HOLDING' && (
        <span className="ml-0.5 text-[0.75em] font-normal">{fmtGap(Math.abs(link.ratePerLapMs))}/lap</span>
      )}
    </span>
  );
}

/**
 * One battle, or a train of close cars, on a line, labelled "Battle for P4" or "Train P4–P7": the cars front to back with the gap and its trend between each
 * pair (#505). A train reads as one line instead of overlapping pairs.
 */
export function BattleGroupLine({
  group,
  className,
  mark,
}: {
  group: BattleGroup;
  className?: string;
  /** Something to draw before each car's name, such as their headshot (#686). */
  mark?: (car: BattleCar) => ReactNode;
}) {
  return (
    <div
      className={cn('flex flex-wrap items-baseline gap-x-2', className)}
      data-testid="battle"
      data-train={group.cars.length > 2 || undefined}
    >
      <span className="text-[0.75em] font-bold uppercase tracking-wide opacity-80" data-testid="battle-label">
        {group.cars.length > 2
          ? `Train P${group.cars[0].position}–P${group.cars[group.cars.length - 1].position}`
          : `Battle for P${group.cars[0].position}`}
      </span>
      {group.cars.map((car, i) => (
        <Fragment key={car.entryId}>
          <span className="inline-flex items-center font-medium">
            {mark?.(car)}
            <span className="mr-1 text-[0.75em] opacity-70">P{car.position}</span>
            {car.driverName}
          </span>
          {i < group.links.length && <GapLink ahead={car} chaser={group.cars[i + 1]} link={group.links[i]} />}
        </Fragment>
      ))}
    </div>
  );
}
