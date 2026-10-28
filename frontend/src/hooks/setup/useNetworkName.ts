import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getNetworkName, saveNetworkName } from '@/lib/setupApi';
import { setupQueryKeys } from './setupQueryKeys';

/** The laptop's network name settings for the setup wizard's network step (#604). */
export function useNetworkName() {
  return useQuery({
    queryKey: setupQueryKeys.networkName(),
    queryFn: getNetworkName,
    retry: false,
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
