import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** Results sent to RaceHub (#27). Refreshes while the page is open so sends show up. */
export function useResultsExports() {
  return useQuery({
    queryKey: adminQueryKeys.resultsExports.all(),
    queryFn: adminApi.resultsExports.list,
    refetchInterval: 15_000,
  });
}

/**
 * Whether the club has a RaceHub key (saved here or in the settings file) or publishes a results site, so is not
 * standalone. {@code undefined} until the answer is known, so a page does not flash "standalone" while loading. Only
 * admins can read it (`enabled`), since the endpoint is admin only.
 */
export function useRaceHubConnected(enabled = true) {
  return useQuery({
    queryKey: adminQueryKeys.resultsExports.all(),
    queryFn: adminApi.resultsExports.list,
    enabled,
    staleTime: 60_000,
    select: list => list.keySource !== 'NONE' || list.publishTarget === 'SITE' || list.publishTarget === 'BOTH',
  }).data;
}

export function useRetryResultsExport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.retry,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

/** Queues an event's results for RaceHub again, and refreshes the sent list. */
export function useReuploadResults() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.reupload,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

export function useSetRaceHubKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.setKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

export function useClearRaceHubKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.clearKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

export function useSetResultsTarget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.setTarget,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

/** Downloads the results site as a zip and saves it through the browser. */
export function useDownloadResultsSite() {
  return useMutation({
    mutationFn: adminApi.resultsExports.downloadSite,
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}

/** Downloads an event's results file and saves it through the browser. */
export function useDownloadResultsExport() {
  return useMutation({
    mutationFn: adminApi.resultsExports.download,
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}
