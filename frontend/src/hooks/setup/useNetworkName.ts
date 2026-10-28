import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getNetworkName, saveNetworkName } from '@/lib/setupApi';
import { setupQueryKeys } from './setupQueryKeys';

/** The laptop's network name settings for the setup wizard's network step (#604). */
export function useNetworkName() {
  return useQuery({
    queryKey: setupQueryKeys.networkName(),
    queryFn: getNetworkName,
    retry: false,
    // After a restart the name is announced within about 30 seconds; look until it is (or the page is left)
    refetchInterval: (query) => {
      const d = query.state.data;
      return d && d.configuredName && !d.announcedHost && !d.restartNeeded && !d.overriddenByEnvironment ? 5000 : false;
    },
  });
}

/** Saves the name for the next start; the answer replaces the cached settings. */
export function useSaveNetworkName() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => saveNetworkName(name),
    onSuccess: (state) => qc.setQueryData(setupQueryKeys.networkName(), state),
  });
}
