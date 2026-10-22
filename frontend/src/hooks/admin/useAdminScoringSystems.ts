import { useQuery } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** The saved scoring systems of one kind, built-ins first. */
export function useScoringSystems(kind: 'RACE' | 'CHAMPIONSHIP') {
  return useQuery({
    queryKey: [...adminQueryKeys.scoringSystems.all(), kind],
    queryFn: () => adminApi.scoringSystems.list(kind),
  });
}
