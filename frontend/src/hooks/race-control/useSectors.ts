import { useQuery } from '@tanstack/react-query';
import { useStomp } from '@/hooks/race-control/useStomp';
import { getBoardSectors } from '@/lib/boardsApi';
import { NO_SECTORS, type SectorBoard } from '@/lib/sectors';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/**
 * A race's sector times (#676), where the club has split decoders. Seeded from the board endpoint, so a screen opened
 * mid-race has them, and so the columns can be laid out before the first lap with every split; then follows the race's
 * sectors topic, which anonymous sessions may subscribe to. {@code sectorCount} is 0 with no split decoders, with no
 * race, or while the seed loads, so a page shows no sector columns.
 */
export function useSectors(raceId: number | null): SectorBoard {
  const { data: live } = useStomp<SectorBoard>(raceId ? `/topic/race/${raceId}/sectors` : null);
  const { data: seed } = useQuery({
    queryKey: publicQueryKeys.boards.sectors(raceId),
    queryFn: () => getBoardSectors(raceId!),
    enabled: raceId !== null,
  });
  return live ?? seed ?? NO_SECTORS;
}
