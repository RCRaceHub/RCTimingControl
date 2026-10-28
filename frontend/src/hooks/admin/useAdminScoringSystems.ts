import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { ScoringConfig } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** The saved scoring systems of one kind, built-ins first. */
export function useScoringSystems(kind: 'RACE' | 'CHAMPIONSHIP') {
  return useQuery({
    queryKey: adminQueryKeys.scoringSystems.ofKind(kind),
    queryFn: () => adminApi.scoringSystems.list(kind),
  });
}

/** Every saved scoring system of both kinds, for the Scoring systems page. */
export function useAllScoringSystems() {
  return useQuery({
    queryKey: adminQueryKeys.scoringSystems.everyKind(),
    queryFn: () => adminApi.scoringSystems.list(),
  });
}

/** Create, change, clone, delete and import mutations; each refreshes every scoring system list. */
export function useScoringSystemMutations() {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: adminQueryKeys.scoringSystems.all() });
  return {
    create: useMutation({ mutationFn: (b: { name: string; config: ScoringConfig }) => adminApi.scoringSystems.create(b), onSuccess: refresh }),
    update: useMutation({ mutationFn: ({ id, ...b }: { id: number; name: string; config: ScoringConfig }) => adminApi.scoringSystems.update(id, b), onSuccess: refresh }),
    clone: useMutation({ mutationFn: ({ id, name }: { id: number; name: string }) => adminApi.scoringSystems.clone(id, name), onSuccess: refresh }),
    remove: useMutation({ mutationFn: (id: number) => adminApi.scoringSystems.delete(id), onSuccess: refresh }),
    importFile: useMutation({ mutationFn: ({ name, text, yaml }: { name: string; text: string; yaml: boolean }) => adminApi.scoringSystems.importText(name, text, yaml), onSuccess: refresh }),
  };
}
