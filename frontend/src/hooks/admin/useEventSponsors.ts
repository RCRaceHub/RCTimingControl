import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** The event's sponsors in display order (#518). */
export function useEventSponsors(eventId: number) {
  return useQuery({
    queryKey: adminQueryKeys.events.sponsors(eventId),
    queryFn: () => adminApi.listSponsors(eventId),
    enabled: Number.isFinite(eventId) && eventId > 0,
  });
}

/** Every change to the list refetches it, so the screen shows what the server holds. */
export function useSponsorMutations(eventId: number) {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: adminQueryKeys.events.sponsors(eventId) });
  return {
    add: useMutation({ mutationFn: (name: string) => adminApi.addSponsor(eventId, name), onSuccess: refresh }),
    rename: useMutation({
      mutationFn: ({ id, name }: { id: number; name: string }) => adminApi.renameSponsor(eventId, id, name),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: number) => adminApi.deleteSponsor(eventId, id), onSuccess: refresh }),
    order: useMutation({ mutationFn: (ids: number[]) => adminApi.orderSponsors(eventId, ids), onSuccess: refresh }),
    logo: useMutation({
      mutationFn: ({ id, file }: { id: number; file: File }) => adminApi.uploadSponsorLogo(eventId, id, file),
      onSuccess: refresh,
    }),
  };
}

/** The calls that change a sponsor list, as {@link useSponsorMutations} and the overlay theme's hook return them. */
export type SponsorMutations = ReturnType<typeof useSponsorMutations>;
