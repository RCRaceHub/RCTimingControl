import { useQuery } from '@tanstack/react-query';
import { useStomp } from './useStomp';
import { getWrongHeat, type WrongHeatDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

/**
 * The drivers whose transponders were seen in a race they are not in, and what has been decided on each. Each
 * broadcast carries the whole list, so the latest one replaces whatever came before; a REST snapshot on mount
 * restores the list when the screen opens (or is reloaded) mid-race.
 */
export function useWrongHeat(raceId: number | null): WrongHeatDto[] {
  const topic = raceId ? `/topic/race/${raceId}/wrong-heat` : null;
  const { data: pushed } = useStomp<WrongHeatDto[]>(topic);

  const { data: snapshot } = useQuery({
    queryKey: raceControlQueryKeys.wrongHeat(raceId),
    queryFn: () => getWrongHeat(raceId!),
    enabled: (raceId ?? 0) > 0,
  });

  return pushed ?? snapshot ?? [];
}
