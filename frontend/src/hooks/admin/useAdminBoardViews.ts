import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { boardViewsAdminApi, type BoardViewInput } from '@/lib/boardViewsApi';
import { adminQueryKeys } from './adminQueryKeys';

/** Every saved board view, for the Board views page. */
export function useBoardViews() {
  return useQuery({ queryKey: adminQueryKeys.boardViews.all(), queryFn: boardViewsAdminApi.list });
}

/** Create, change and delete mutations; each refreshes the list. */
export function useBoardViewMutations() {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: adminQueryKeys.boardViews.all() });
  return {
    create: useMutation({ mutationFn: (b: BoardViewInput) => boardViewsAdminApi.create(b), onSuccess: refresh }),
    update: useMutation({
      mutationFn: ({ id, ...b }: BoardViewInput & { id: number }) => boardViewsAdminApi.update(id, b),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: number) => boardViewsAdminApi.delete(id), onSuccess: refresh }),
  };
}
