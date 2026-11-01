import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/**
 * The events RaceHub has released for timing, asked fresh each time the picker opens. A RaceHub that cannot be
 * reached still answers, with `error` set, so this query only fails when RCTC itself does.
 */
export function useRaceHubEvents(enabled: boolean) {
  return useQuery({
    queryKey: adminQueryKeys.racehub.events(),
    queryFn: () => adminApi.raceHubEvents.list(),
    enabled,
    staleTime: 0,
  });
}

export function useSetRaceHubEventsAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventsUrl: string) => adminApi.raceHubEvents.setAddress(eventsUrl),
    onSuccess: data => qc.setQueryData(adminQueryKeys.racehub.events(), data),
  });
}

/** Takes an offered event. The events list changes (a new event, or a link on one), so it is refreshed. */
export function useTakeRaceHubEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (raceHubEventId: string) => adminApi.raceHubEvents.take(raceHubEventId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.all() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.events() });
    },
  });
}
