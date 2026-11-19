import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { SplitDecoderInput } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/**
 * The split-loop decoders with their connection state (#672). The state changes without anything being saved, so
 * the list is read again every few seconds while the page is open.
 */
export function useSplitDecoders() {
  return useQuery({
    queryKey: adminQueryKeys.splitDecoders.all(),
    queryFn: adminApi.splitDecoders.list,
    refetchInterval: 5_000,
  });
}

export function useSaveSplitDecoder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number | null; body: SplitDecoderInput }) =>
      id === null ? adminApi.splitDecoders.create(body) : adminApi.splitDecoders.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.splitDecoders.all() }),
  });
}

export function useDeleteSplitDecoder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminApi.splitDecoders.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.splitDecoders.all() }),
  });
}
