import { useQuery } from '@tanstack/react-query';
import { useStomp } from '@/hooks/race-control/useStomp';
import { getBoardBattles } from '@/lib/boardsApi';
import type { BattleGroup } from '@/lib/battles';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/**
 * The on-track battles in a race (#505), as the timing server works them out. Seeded from the board endpoint, so a
 * screen opened mid-race shows them before the next passing, then follows the race's battles topic, which anonymous
 * sessions may subscribe to. Empty with no race.
 */
export function useBattles(raceId: number | null): BattleGroup[] {
  const { data: live } = useStomp<BattleGroup[]>(raceId ? `/topic/race/${raceId}/battles` : null);
  const { data: seed } = useQuery({
    queryKey: publicQueryKeys.boards.battles(raceId),
    queryFn: () => getBoardBattles(raceId!),
    enabled: raceId !== null,
  });
  return live ?? seed ?? [];
}
