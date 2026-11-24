import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { adminApi, type AuditFilterParams } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** One page of the audit log. The previous page stays on screen while the next one loads. */
export function useAdminAudit(filter: AuditFilterParams, page: number, size: number) {
  return useQuery({
    queryKey: adminQueryKeys.audit.page({ ...filter, size }, page),
    queryFn: () => adminApi.audit.search(filter, page, size),
    placeholderData: keepPreviousData,
  });
}
