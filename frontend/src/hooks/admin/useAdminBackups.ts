import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useAdminBackups() {
  return useQuery({
    queryKey: adminQueryKeys.backups.all(),
    queryFn: adminApi.backups.list,
  });
}

export function useBackupNow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adminApi.backups.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.backups.all() }),
  });
}

export function useRetentionPolicy() {
  return useQuery({
    queryKey: adminQueryKeys.retention.policy(),
    queryFn: adminApi.retention.policy,
  });
}
