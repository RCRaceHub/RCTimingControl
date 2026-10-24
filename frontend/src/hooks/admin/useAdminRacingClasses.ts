import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { RacingClassInput } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** Racing classes are listed by {@link useRacingClasses}; these change them and refresh that list. */
export function useCreateRacingClass() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RacingClassInput) => adminApi.racingClasses.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.racingClasses.all() }),
  });
}

export function useUpdateRacingClass() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RacingClassInput }) => adminApi.racingClasses.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.racingClasses.all() }),
  });
}

export function useDeleteRacingClass() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminApi.racingClasses.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.racingClasses.all() }),
  });
}
