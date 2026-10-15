import { useQuery } from '@tanstack/react-query';
import { getRaceClock } from '@/lib/boardsApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

/**
 * The race clock of a running or stopped race, with when it was read. It is read again when the race starts,
 * stops or resumes (the status is part of the key), since a stoppage moves the end.
 */
export function useRaceClock(raceId: number | null, status: string | undefined) {
  const { data: clock, dataUpdatedAt: readAt } = useQuery({
    queryKey: raceControlQueryKeys.raceClock(raceId, status),
    queryFn: () => getRaceClock(raceId!),
    enabled: raceId != null && (status === 'RUNNING' || status === 'STOPPED'),
    // Not kept: a clock read before an earlier stoppage would show the wrong time until the new one arrives
    gcTime: 0,
  });
  return { clock, readAt };
}
