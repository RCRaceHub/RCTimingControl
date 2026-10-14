import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { SaveEntryFeedRequest } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** The event's entry feed, or null when it has none (#42). */
export function useEntryFeed(eventId: number) {
  return useQuery({
    queryKey: adminQueryKeys.events.entryFeed(eventId),
    queryFn: () => adminApi.getEntryFeed(eventId),
    enabled: Number.isFinite(eventId) && eventId > 0,
  });
}

export function useSaveEntryFeed(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: SaveEntryFeedRequest) => adminApi.saveEntryFeed(eventId, request),
    onSuccess: feed => qc.setQueryData(adminQueryKeys.events.entryFeed(eventId), feed),
  });
}

export function useDeleteEntryFeed(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => adminApi.deleteEntryFeed(eventId),
    onSuccess: () => qc.setQueryData(adminQueryKeys.events.entryFeed(eventId), null),
  });
}

/** Fetches now. The feed comes back with the outcome, including a failure. */
export function useFetchEntryFeed(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => adminApi.fetchEntryFeed(eventId),
    onSuccess: feed => qc.setQueryData(adminQueryKeys.events.entryFeed(eventId), feed),
  });
}

/**
 * Previews or imports the file the feed is holding. An import that applies invalidates the event's key, which
 * by prefix also refreshes its entries and the feed.
 */
export function useEntryFeedImport(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { dryRun: boolean }) =>
      args.dryRun ? adminApi.previewEntryFeed(eventId) : adminApi.applyEntryFeed(eventId),
    onSuccess: result => {
      if (result.applied) {
        qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      }
    },
  });
}
