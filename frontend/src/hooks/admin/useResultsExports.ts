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

export function useRetryResultsExport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.retry,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.resultsExports.all() }),
  });
}

export function useSetResultsAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.resultsExports.setAddress,
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
