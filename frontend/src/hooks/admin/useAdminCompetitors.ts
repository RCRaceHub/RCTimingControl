import { toast } from 'sonner';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, type NameVisibility } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useAdminCompetitorsList() {
  return useQuery({
    queryKey: adminQueryKeys.competitors.all(),
    queryFn: adminApi.competitors.list,
  });
}

/** Sets or clears how a competitor's name is said aloud, then refreshes the list (#119). */
export function useSetSpokenName() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, spokenName }: { id: number; spokenName: string }) =>
      adminApi.competitors.setSpokenName(id, spokenName),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.competitors.all() }),
  });
}

/** Sets how a competitor's name is shown on public screens, then refreshes the list (#265). */
export function useSetNameVisibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, nameVisibility }: { id: number; nameVisibility: NameVisibility }) =>
      adminApi.competitors.setNameVisibility(id, nameVisibility),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.competitors.all() }),
  });
}

/** Who changed how a competitor's name is said (admin only), newest first (#119). */
export function useCompetitorChanges(id: number, enabled: boolean) {
  return useQuery({
    queryKey: adminQueryKeys.competitors.changes(id),
    queryFn: () => adminApi.competitors.changes(id),
    enabled,
  });
}

/** Competitors that may be one person entered twice, for an admin to review (#123). */
export function usePossibleDuplicates(enabled: boolean) {
  return useQuery({
    queryKey: adminQueryKeys.competitors.possibleDuplicates(),
    queryFn: adminApi.competitors.possibleDuplicates,
    enabled,
  });
}

/** What merging would move, once both competitors are chosen (#123). */
export function useMergePreview(keepId: number | null, duplicateId: number | null) {
  return useQuery({
    queryKey: adminQueryKeys.competitors.mergePreview(keepId ?? 0, duplicateId ?? 0),
    queryFn: () => adminApi.competitors.mergePreview(keepId!, duplicateId!),
    enabled: keepId != null && duplicateId != null,
    // A preview is only true at the moment it is read
    gcTime: 0,
  });
}

/**
 * Merges a duplicate into the competitor to keep, then refreshes every admin list: the entries of
 * every event now point at the competitor that was kept (#123).
 */
export function useMergeCompetitors() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ keepId, duplicateId }: { keepId: number; duplicateId: number }) =>
      adminApi.competitors.merge(keepId, duplicateId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.all }),
  });
}

/** Downloads everything held about a competitor and saves it through the browser (#263). */
export function useExportCompetitorData() {
  return useMutation({
    mutationFn: (id: number) => adminApi.competitors.exportPersonalData(id),
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      // Some browsers start the save after the click returns
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    onError: () => toast.error('Could not download the data. Try again.'),
  });
}

/** Anonymises a competitor, then refreshes every admin list that showed their name (#263). */
export function useAnonymiseCompetitor() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminApi.competitors.anonymise(id),
    onSuccess: () => queryClient.invalidateQueries(),
  });
}
