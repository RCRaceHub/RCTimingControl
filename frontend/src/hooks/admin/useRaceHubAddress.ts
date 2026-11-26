import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/**
 * Saves RaceHub's address, the one place it is set. Results, the event picker, the class and format lists and imports
 * all find their paths from it, so each of those is asked again afterwards.
 */
export function useSetRaceHubAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (address: string) => adminApi.raceHubAddress.set(address),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.events() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.classes() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.formats() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.raceHubConnection.all() });
    },
  });
}
