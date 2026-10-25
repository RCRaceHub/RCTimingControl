import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getRaceMedia, type RaceMediaDto } from '@/lib/boardsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** Pictures change when an official uploads one, not during a heat, so they are read once a minute. */
const MEDIA_POLL_MS = 60_000;

/**
 * The headshots, car photos and driver sponsors of the cars in a race, by entry id (#521). Empty while loading, for
 * no race, and for cars with nothing to show; a failed read leaves the screens as they were without pictures.
 */
export function useRaceMedia(raceId: number | null | undefined): Map<number, RaceMediaDto> {
  const { data } = useQuery({
    queryKey: publicQueryKeys.boards.media(raceId ?? null),
    queryFn: () => getRaceMedia(raceId!),
    enabled: raceId != null,
    refetchInterval: MEDIA_POLL_MS,
  });
  return useMemo(() => new Map((data ?? []).map((m) => [m.entryId, m])), [data]);
}
