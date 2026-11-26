import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { EntryFeedDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';
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

/**
 * Takes an offered event, then fetches its entries straight away so the official is not left waiting for the
 * scheduled fetch (every few minutes). The fetch only holds a new revision for review; nothing is imported until the
 * official confirms. A fetch that fails does not undo the take: the event exists and its feed is saved, so the
 * failure comes back as `feed` with the reason, or as `feed: null` with `fetchError` when the call itself failed.
 * The events list changes (a new event, or a link on one), so it is refreshed.
 */
export function useTakeRaceHubEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (raceHubEventId: string) => {
      const event = await adminApi.raceHubEvents.take(raceHubEventId);
      let feed: EntryFeedDto | null = null;
      let fetchError: string | null = null;
      try {
        feed = await adminApi.fetchEntryFeed(event.id);
      } catch (err) {
        fetchError = getApiErrorMessage(err, 'The fetch failed');
      }
      return { event, feed, fetchError };
    },
    onSuccess: ({ event, feed }) => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.all() });
      qc.invalidateQueries({ queryKey: adminQueryKeys.racehub.events() });
      if (feed) qc.setQueryData(adminQueryKeys.events.entryFeed(event.id), feed);
    },
  });
}

/** Finds or creates the event for an Entry Export file, so it can be imported without making an event first. */
export function useRaceHubMeeting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (exportDocument: unknown) => adminApi.raceHubMeeting.findOrCreate(exportDocument),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.events.all() }),
  });
}
