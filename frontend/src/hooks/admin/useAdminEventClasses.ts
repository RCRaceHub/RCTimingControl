import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { AddEventClassRequest } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useRacingClasses() {
  return useQuery({
    queryKey: adminQueryKeys.racingClasses.all(),
    queryFn: () => adminApi.listRacingClasses(),
  });
}

export function useAddEventClass(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AddEventClassRequest) => adminApi.addEventClass(eventId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) }),
  });
}

export function useRemoveEventClass(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { classId: number; withdrawEntries: boolean }) =>
      adminApi.removeEventClass(eventId, args.classId, args.withdrawEntries),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.all() });
    },
  });
}

export function useUpdateEventClassOverrides(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { classId: number; override: Record<string, unknown> }) =>
      adminApi.updateOverrides(eventId, args.classId, args.override),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) }),
  });
}

export function useCombineClasses(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventClassIds: number[]) => adminApi.combineClasses(eventId, eventClassIds),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) }),
  });
}
