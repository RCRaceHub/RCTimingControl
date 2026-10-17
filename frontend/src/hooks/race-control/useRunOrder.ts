import { useQuery } from '@tanstack/react-query';
import { getRunOrder, type RunOrderItemDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/** {@code fast} polls every few seconds, for while auto run moves races on without anyone pressing anything. */
export function useRunOrder(eventId: number | null, fast = false) {
  return useQuery<RunOrderItemDto[]>({
    queryKey: raceControlQueryKeys.runOrder(eventId ?? -1),
    queryFn: () => getRunOrder(eventId!),
    enabled: eventId !== null,
    staleTime: fast ? 2_000 : 10_000,
    refetchInterval: fast ? 3_000 : 15_000,
  });
}
