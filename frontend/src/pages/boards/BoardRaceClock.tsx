import { useBoardRaceClock } from '@/hooks/useBoardRaceClock';
import { useRaceClockQuery } from '@/hooks/useBoardQueries';
import { cn } from '@/lib/utils';
import { BOARD_POLL_MS } from './boardFormat';

/**
 * The race clock on a live board: the time left (or the time so far in a race with no length), which turns the
 * overrun colour and counts down the time left to finish once the race time is up. The colour is red unless the
 * board's address gives {@code overrun=RRGGBB}.
 */
export function BoardRaceClock({ raceId, overrunColour, className }: {
  raceId: number | null;
  overrunColour: string | null;
  className?: string;
}) {
  const { data: clock, dataUpdatedAt: clockReadAt } = useRaceClockQuery(raceId, { intervalMs: BOARD_POLL_MS });
  const { text, overrun } = useBoardRaceClock(raceId, clock, clockReadAt);
  if (text === null) return null;
  return (
    <span
      className={cn('font-bold tabular-nums', className, overrun && overrunColour === null && 'text-destructive')}
      style={overrun && overrunColour !== null ? { color: overrunColour } : undefined}
      role="timer"
      aria-label={overrun ? 'Time left to finish' : 'Race clock'}
    >
      {text}
    </span>
  );
}
