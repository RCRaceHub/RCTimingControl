import { useEffect, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useStomp } from '@/hooks/race-control/useStomp';
import type { LiveTimingRowDto, RaceStateChangeDto } from '@/lib/raceControlApi';
import { getBoardLiveTiming, getNowNext } from '@/lib/boardsApi';
import { holdBackLapped } from '@/lib/lapProgress';
import { BOARD_POLL_MS } from './boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/**
 * What a board shows: the event's now-and-next, polled, and while a race is on track its running order,
 * sorted by position. The order is seeded from the board endpoint, so a board opened mid-race shows the field
 * before the next passing, then follows the race's STOMP timing topic, which anonymous sessions may subscribe to.
 * A stop, resume or finish on the state topic refreshes every board query rather than waiting for the next poll.
 */
export function useBoardRace(eventId: number | null) {
  const queryClient = useQueryClient();

  // Polling keeps retrying, so a board that lost the server shows the last answer it had; before its first
  // answer it reports isError
  const { data: nowNext, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.nowNext(eventId),
    queryFn: () => getNowNext(eventId),
    refetchInterval: BOARD_POLL_MS,
  });
  const currentRace = nowNext?.currentRace ?? null;
  const raceId = currentRace?.raceId ?? null;

  const { data: liveRows } = useStomp<LiveTimingRowDto[]>(raceId ? `/topic/race/${raceId}/timing` : null);
  const { data: stateChange } = useStomp<RaceStateChangeDto>(raceId ? `/topic/race/${raceId}/state` : null);
  const { data: seedRows } = useQuery({
    queryKey: publicQueryKeys.boards.liveTiming(raceId),
    queryFn: () => getBoardLiveTiming(raceId!),
    enabled: raceId !== null,
  });

  useEffect(() => {
    if (stateChange) {
      void queryClient.invalidateQueries({ queryKey: publicQueryKeys.boards.all });
    }
  }, [stateChange, queryClient]);

  const rows = useMemo(() => {
    const source = liveRows ?? seedRows ?? [];
    return holdBackLapped([...source].sort((a, b) => a.position - b.position));
  }, [liveRows, seedRows]);

  return { nowNext, isPending, isError, currentRace, raceId, rows };
}
