import { useQuery } from '@tanstack/react-query';
import { useStomp } from './useStomp';
import { getJumpStarts, type JumpStartDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

/**
 * The cars that have crossed the line before their turn in a stagger start. Each broadcast carries the whole list, so
 * the latest one replaces whatever came before; a REST snapshot on mount restores the list when the screen opens
 * (or is reloaded) mid-race.
 */
export function useJumpStarts(raceId: number | null): JumpStartDto[] {
  const topic = raceId ? `/topic/race/${raceId}/jump-start` : null;
  const { data: pushed } = useStomp<JumpStartDto[]>(topic);

  const { data: snapshot } = useQuery({
    queryKey: raceControlQueryKeys.jumpStarts(raceId),
    queryFn: () => getJumpStarts(raceId!),
    enabled: (raceId ?? 0) > 0,
  });

  return pushed ?? snapshot ?? [];
}
