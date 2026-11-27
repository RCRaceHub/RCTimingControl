import { useQuery } from '@tanstack/react-query';
import { fetchRaceHubStatus } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/**
 * Whether the link to RaceHub is alive and how results sending stands. The server answers from a check it makes
 * once a minute, so polling every 30 seconds is cheap and never waits on RaceHub.
 */
export function useRaceHubStatus() {
  return useQuery({
    queryKey: raceControlQueryKeys.raceHubStatus(),
    queryFn: fetchRaceHubStatus,
    staleTime: 0,
    refetchInterval: 30_000,
  });
}
