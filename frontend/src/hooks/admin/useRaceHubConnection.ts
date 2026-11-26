import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

/**
 * Runs "Test connection": asks RaceHub with the address and key now in use. The server also updates what race
 * control's RaceHub indicator shows, so that is asked again afterwards.
 */
export function useTestRaceHubConnection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: adminApi.raceHubConnection.test,
    onSettled: () => qc.invalidateQueries({ queryKey: raceControlQueryKeys.raceHubStatus() }),
  });
}
