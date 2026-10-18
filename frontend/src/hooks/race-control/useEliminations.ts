import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  eliminateEntry,
  falseStartEntry,
  getEliminations,
  reviveEntry,
  undoElimination,
} from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/**
 * A Shikabane race's eliminations and the actions that change them. `shikabane` is false for any other race, so the
 * caller shows nothing. `deadOrder` maps each dead entry to its place in the order of elimination (1 = first to die).
 */
export function useEliminations(raceId: number) {
  const queryClient = useQueryClient();
  const key = raceControlQueryKeys.eliminations(raceId);
  const query = useQuery({ queryKey: key, queryFn: () => getEliminations(raceId), retry: false,
    // Another official may mark a car; this is not pushed over the race topics
    refetchInterval: 5000,
  });

  // A mark changes the result of a finished race and the history, so both are refetched
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.resultSnapshot(raceId) });
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.raceHistory(raceId) });
  };

  const deadOrder = new Map<number, number>();
  const revivals = new Map<number, number>();
  for (const row of query.data?.rows ?? []) {
    if (row.revivedAt === null) deadOrder.set(row.entryId, deadOrder.size + 1);
    else revivals.set(row.entryId, (revivals.get(row.entryId) ?? 0) + 1);
  }

  return {
    shikabane: query.data?.shikabane ?? false,
    deadOrder,
    revivals,
    eliminate: useMutation({ mutationFn: (entryId: number) => eliminateEntry(raceId, entryId), onSuccess: refresh }),
    revive: useMutation({ mutationFn: (entryId: number) => reviveEntry(raceId, entryId), onSuccess: refresh }),
    falseStart: useMutation({ mutationFn: (entryId: number) => falseStartEntry(raceId, entryId), onSuccess: refresh }),
    undo: useMutation({ mutationFn: (entryId: number) => undoElimination(raceId, entryId), onSuccess: refresh }),
  };
}
