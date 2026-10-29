import type { BattleLink } from '@/lib/battles';
import { cn } from '@/lib/utils';

/** The row classes that mark the chasing car's row: a hot gradient and a white edge, as on a broadcast tower (#681). */
export const BATTLE_ROW_CLASS = 'border-l-4 border-l-white bg-gradient-to-r from-destructive/70 to-transparent';

const TREND_ARROW = { CLOSING: '▲', GROWING: '▼', HOLDING: '▬', UNKNOWN: '' } as const;

/** The gap as seconds to one decimal place, e.g. "0.8s". */
function fmtGap(ms: number): string {
  return `${(ms / 1000).toFixed(1)}s`;
}

/**
 * What a battle row says in place of its plain gap (#681): pulsing chevrons and "BATTLE" over the gap to the car ahead
 * and whether it is closing, growing or holding. The pulse stops under reduced motion. Drawn by the overlay's tower and
 * table for the chasing car only.
 */
export function BattleRowTag({ link, className }: { link: BattleLink; className?: string }) {
  return (
    <span
      className={cn('flex flex-col items-end leading-tight', className)}
      data-testid="battle-row-tag"
      data-trend={link.trend}
    >
      <span className="battle-pulse text-[0.6em] font-bold uppercase tracking-wider" aria-hidden="true">
        »» Battle
      </span>
      <span className="font-semibold tabular-nums">
        <span className="sr-only">Battle, </span>
        {fmtGap(link.gapMs)}
        {TREND_ARROW[link.trend] && ` ${TREND_ARROW[link.trend]}`}
      </span>
    </span>
  );
}
