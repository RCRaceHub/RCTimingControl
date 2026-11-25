import { useMutation, useQueryClient } from '@tanstack/react-query';
import { addEntryToHeat } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/** Adds an entry that is in no heat yet to a pending heat; refreshes everything race control shows. */
export function useAddEntryToHeat() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (v: { raceId: number; entryId: number; allRounds: boolean }) =>
      addEntryToHeat(v.raceId, { entryId: v.entryId, allRounds: v.allRounds }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all }),
  });
}
