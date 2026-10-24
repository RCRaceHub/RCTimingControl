import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { placesGained } from '@/lib/lapProgress';
import { cn } from '@/lib/utils';

/**
 * An arrow for the places a car has gained or lost against its grid place (#479): green up, red down, nothing
 * when it has held its place or has no grid place. Meant for finals, where the grid is the whole point.
 */
export function PositionArrow({ row, className }: { row: LiveTimingRowDto; className?: string }) {
  const gained = placesGained(row);
  if (gained === null || gained === 0) return null;
  const up = gained > 0;
  return (
    <span
      role="img"
      aria-label={up ? `Up ${gained}` : `Down ${-gained}`}
      title={up ? `Up ${gained} from the grid` : `Down ${-gained} from the grid`}
      data-direction={up ? 'up' : 'down'}
      className={cn('ml-1 whitespace-nowrap text-[0.7em] font-semibold', up ? 'text-emerald-400' : 'text-rose-400', className)}
    >
      {up ? '▲' : '▼'}{Math.abs(gained)}
    </span>
  );
}
