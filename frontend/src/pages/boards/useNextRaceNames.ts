import { useQuery } from '@tanstack/react-query';
import { getTicker, type NowNextDto } from '@/lib/boardsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { BOARD_POLL_MS } from './boardFormat';

/**
 * The next race's driver and marshal names from the ticker. The ticker polls on its own timer, so its names are ignored
 * until it is talking about the race the board shows as next; {@code marshalsPendingText} is set while a bump-up
 * final's marshals are not known yet.
 */
export function useNextRaceNames(nowNext: NowNextDto | undefined, eventId: number | null) {
  const nextRace = nowNext?.nextRace ?? null;
  // Pinned to the event this board shows, so the names can't come from a different event than the race above them
  const tickerEventId = eventId ?? nowNext?.eventId ?? null;
  const { data: ticker } = useQuery({
    queryKey: publicQueryKeys.boards.ticker(tickerEventId),
    queryFn: () => getTicker(tickerEventId),
    enabled: nextRace !== null,
    refetchInterval: BOARD_POLL_MS,
  });
  const tickerIsCurrent = nextRace !== null && ticker?.nextRace === nextRace.label;
  return {
    ticker,
    nextDrivers: tickerIsCurrent ? ticker.nextDrivers : [],
    nextMarshals: tickerIsCurrent ? ticker.marshals : [],
    marshalsPendingText:
      tickerIsCurrent && ticker.marshalsPending ? 'To be confirmed when the final below finishes' : undefined,
  };
}
