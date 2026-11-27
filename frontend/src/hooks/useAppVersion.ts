import { useQuery } from '@tanstack/react-query';
import { getAboutInfo } from '@/lib/api';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** The anonymous About info (version, build time, network addresses), loaded once per page load. */
export function useAboutInfo() {
  return useQuery({
    queryKey: publicQueryKeys.about(),
    queryFn: getAboutInfo,
    staleTime: Infinity,
  });
}

/** The running app's version, or undefined while loading or if the About endpoint failed. */
export function useAppVersion(): string | undefined {
  return useAboutInfo().data?.version;
}
