import { useQuery } from '@tanstack/react-query';
import { getAboutInfo } from '@/lib/api';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** The running app's version from the anonymous About endpoint, or undefined while loading or if it failed. */
export function useAppVersion(): string | undefined {
  const { data } = useQuery({
    queryKey: publicQueryKeys.about(),
    queryFn: getAboutInfo,
    staleTime: Infinity,
  });
  return data?.version;
}
