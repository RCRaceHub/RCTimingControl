import { useQuery } from '@tanstack/react-query';
import { getEventResults, getRaceResult, type EventResultsDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/** Every finished race of an event with real names and starting grid, for the officials' printouts. */
export function useEventResults(eventId: number | null) {
  return useQuery<EventResultsDto>({
    queryKey: raceControlQueryKeys.eventResults(eventId ?? -1),
    queryFn: () => getEventResults(eventId!),
    enabled: eventId !== null,
    staleTime: 30_000,
  });
}

/** One finished race in the same shape, for the heat result printout. */
export function useRaceResult(raceId: number | null) {
  return useQuery<EventResultsDto>({
    queryKey: raceControlQueryKeys.raceResult(raceId ?? -1),
    queryFn: () => getRaceResult(raceId!),
    enabled: raceId !== null,
    staleTime: 30_000,
  });
}
