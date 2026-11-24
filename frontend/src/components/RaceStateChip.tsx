import { cn } from '@/lib/utils';

type RaceState = 'PENDING' | 'GRID' | 'RUNNING' | 'STOPPED' | 'FINISHED';

const CHIP: Record<RaceState, { label: string; className: string }> = {
  PENDING: { label: 'Pending', className: 'bg-muted text-muted-foreground' },
  GRID: { label: 'On the grid', className: 'bg-warning/15 text-warning' },
  RUNNING: { label: 'Running', className: 'bg-success/15 text-success' },
  STOPPED: { label: 'Stopped', className: 'bg-destructive/15 text-destructive' },
  FINISHED: { label: 'Finished', className: 'bg-info/15 text-info' },
};

/**
 * The race's state in words and a colour, beside its title on race control and the referee screen (#769), so a
 * stopped or gridded race is not told apart from a running one by a small dot alone.
 */
export function RaceStateChip({ status, className }: { status: RaceState; className?: string }) {
  const chip = CHIP[status];
  return (
    <span
      className={cn('inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide', chip.className, className)}
      role="status"
      aria-label={`Race state: ${chip.label}`}
    >
      {chip.label}
    </span>
  );
}
