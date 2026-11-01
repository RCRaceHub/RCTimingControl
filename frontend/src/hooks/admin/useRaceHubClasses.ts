import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { LinkRaceHubClassRequest } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/**
 * The club's classes as RaceHub lists them, asked fresh each time. A RaceHub that cannot be reached still answers,
 * with `error` set, so this query only fails when RCTC itself does. `refetch` asks RaceHub again.
 */
export function useRaceHubClasses(enabled = true) {
  return useQuery({
    queryKey: adminQueryKeys.racehub.classes(),
    queryFn: () => adminApi.raceHubClasses.list(),
    enabled,
    staleTime: 0,
  });
}

/**
 * How many of the club's RaceHub classes have no link yet, for the badge on the menu entry and the page. Shares the
 * list's query but may be a minute old, so the menu does not ask RaceHub on every page. Zero when RaceHub cannot be
 * asked.
 */
export function useUnlinkedRaceHubClassCount(enabled = true) {
  return useQuery({
    queryKey: adminQueryKeys.racehub.classes(),
    queryFn: () => adminApi.raceHubClasses.list(),
    enabled,
    staleTime: 60_000,
    select: list => list.classes.filter(c => c.linkedRacingClassId == null).length,
  });
}

/** Links club classes to racing classes for the whole club; new racing classes show up in the class lists. */
export function useLinkRaceHubClasses() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (requests: LinkRaceHubClassRequest[]) => adminApi.raceHubClasses.link(requests),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.classes() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.racingClasses.all() });
    },
  });
}
