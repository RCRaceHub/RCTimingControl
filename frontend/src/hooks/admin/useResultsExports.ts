import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';
import { useDisabledModules } from './useModules';

const RESULTS_EXPORT_MODULE = 'results-export';

/** The club's RaceHub address and key. Core: it works whether or not results are sent. */
export function useRaceHubConnection() {
  return useQuery({
    queryKey: adminQueryKeys.raceHubConnection.all(),
    queryFn: adminApi.raceHubConnection.get,
    refetchInterval: 15_000,
  });
}

/**
 * Results sent to RaceHub (#27) with the RaceHub connection. Refreshes while the page is open so sends show up.
 * While the results export module is off its part is empty ({@code moduleOff}) and is not asked for.
 */
export function useResultsExports() {
  const moduleOff = useDisabledModules().has(RESULTS_EXPORT_MODULE);
  const connection = useRaceHubConnection();
  const exports = useQuery({
    queryKey: adminQueryKeys.resultsExports.all(),
    queryFn: adminApi.resultsExports.list,
    refetchInterval: 15_000,
    enabled: !moduleOff,
  });
  const data =
    connection.data && (moduleOff || exports.data)
      ? {
          ...connection.data,
          publishTarget: exports.data?.publishTarget ?? ('NONE' as const),
          chosenTarget: exports.data?.chosenTarget ?? null,
          exports: exports.data?.exports ?? [],
        }
      : undefined;
  return {
    data,
    moduleOff,
    isLoading: connection.isLoading || (!moduleOff && exports.isLoading),
    isError: connection.isError || (!moduleOff && exports.isError),
    refetch: () => {
      void connection.refetch();
      if (!moduleOff) void exports.refetch();
    },
  };
}

/**
 * Whether the club has a RaceHub key (saved here or in the settings file) or publishes a results site, so is not
 * standalone. {@code undefined} until the answer is known, so a page does not flash "standalone" while loading. Only
 * admins can read it (`enabled`), since the endpoints are admin only.
 */
export function useRaceHubConnected(enabled = true) {
  const moduleOff = useDisabledModules().has(RESULTS_EXPORT_MODULE);
  const connection = useQuery({
    queryKey: adminQueryKeys.raceHubConnection.all(),
    queryFn: adminApi.raceHubConnection.get,
    enabled,
    staleTime: 60_000,
  });
  const exports = useQuery({
    queryKey: adminQueryKeys.resultsExports.all(),
    queryFn: adminApi.resultsExports.list,
    enabled: enabled && !moduleOff,
    staleTime: 60_000,
  });
  if (!connection.data || (!moduleOff && !exports.data)) return undefined;
  const target = exports.data?.publishTarget;
  return connection.data.keySource !== 'NONE' || target === 'SITE' || target === 'BOTH';
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
    mutationFn: adminApi.raceHubConnection.setKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.raceHubConnection.all() }),
  });
}

export function useClearRaceHubKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.raceHubConnection.clearKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.raceHubConnection.all() }),
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
