import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDidNotStart, markDidNotStart, undoDidNotStart } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/**
 * The cars marked as not started (DNS) in a race, and the actions that mark or unmark one. `marked` is the set of
 * entry ids. A mark changes the result of a finished race and the history, so both are refetched.
 */
export function useDidNotStart(raceId: number, options: { enabled?: boolean } = {}) {
  const queryClient = useQueryClient();
  const key = raceControlQueryKeys.didNotStart(raceId);
  const query = useQuery({
    queryKey: key,
    queryFn: () => getDidNotStart(raceId),
    enabled: options.enabled ?? true,
    // Another official may mark a car; this is not pushed over the race topics
    refetchInterval: 5000,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.resultSnapshot(raceId) });
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.raceHistory(raceId) });
  };

  return {
    marked: new Set(query.data?.entryIds ?? []),
    mark: useMutation({ mutationFn: (entryId: number) => markDidNotStart(raceId, entryId), onSuccess: refresh }),
    undo: useMutation({ mutationFn: (entryId: number) => undoDidNotStart(raceId, entryId), onSuccess: refresh }),
  };
}
