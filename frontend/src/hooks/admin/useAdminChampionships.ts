import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { ChampionshipDto, PointsScaleEntryDto } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useChampionshipsList() {
  return useQuery({
    queryKey: adminQueryKeys.championships.all(),
    queryFn: adminApi.championships.list,
  });
}

export function useChampionshipDetail(id: number) {
  return useQuery({
    queryKey: adminQueryKeys.championships.detail(id),
    queryFn: () => adminApi.championships.get(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

export function useCreateChampionship() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<ChampionshipDto, 'id'>) => adminApi.championships.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.all() }),
  });
}

export function useUpdateChampionship(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<ChampionshipDto, 'id'>) => adminApi.championships.update(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.all() });
    },
  });
}

export function useAddChampionshipClass(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { racingClassId: number; bestXFromYX: number | null; bestXFromYY: number | null }) =>
      adminApi.championships.addClass(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useRemoveChampionshipClass(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (racingClassId: number) => adminApi.championships.removeClass(id, racingClassId),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useLinkChampionshipEvent(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { eventId: number; roundNumber: number }) =>
      adminApi.championships.linkEvent(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useUnlinkChampionshipEvent(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: number) => adminApi.championships.unlinkEvent(id, eventId),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useApplyScoringSystem(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (systemId: number) => adminApi.championships.applyScoringSystem(id, systemId),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useReplacePointsScale(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entries: PointsScaleEntryDto[]) =>
      adminApi.championships.replacePointsScale(id, entries),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.championships.detail(id) }),
  });
}

export function useChampionshipExclusions(id: number) {
  return useQuery({
    queryKey: adminQueryKeys.championships.exclusions(id),
    queryFn: () => adminApi.championships.listExclusions(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}

export function useCreateExclusion(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { driverId: number; eventId: number; reason: string }) =>
      adminApi.championships.createExclusion(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.exclusions(id) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.standings(id) });
    },
  });
}

export function useDeleteExclusion(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (exclusionId: number) => adminApi.championships.deleteExclusion(id, exclusionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.exclusions(id) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.championships.standings(id) });
    },
  });
}

export function useChampionshipStandings(id: number) {
  return useQuery({
    queryKey: adminQueryKeys.championships.standings(id),
    queryFn: () => adminApi.championships.getStandings(id),
    enabled: Number.isFinite(id) && id > 0,
  });
}
