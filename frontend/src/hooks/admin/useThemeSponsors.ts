import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { overlayThemesAdminApi } from '@/lib/overlayThemesApi';
import { adminQueryKeys } from './adminQueryKeys';
import { publicQueryKeys } from '../publicQueryKeys';
import type { SponsorMutations } from './useEventSponsors';

/** The sponsors an overlay theme owns, in display order; none until a theme is picked. */
export function useThemeSponsors(themeId: number | null) {
  return useQuery({
    queryKey: adminQueryKeys.overlayThemes.sponsors(themeId ?? 0),
    queryFn: () => overlayThemesAdminApi.listSponsors(themeId as number),
    enabled: themeId !== null,
  });
}

/** Every change to the list refetches it (and the overlay's own cached sponsors), so the screen shows what the server holds. */
export function useThemeSponsorMutations(themeId: number): SponsorMutations {
  const qc = useQueryClient();
  const refresh = () => Promise.all([
    qc.invalidateQueries({ queryKey: adminQueryKeys.overlayThemes.sponsors(themeId) }),
    // The designer's preview reads the same sponsors through the public boards key
    qc.invalidateQueries({ queryKey: publicQueryKeys.boards.sponsors(null).slice(0, 2) }),
  ]);
  return {
    add: useMutation({ mutationFn: (name: string) => overlayThemesAdminApi.addSponsor(themeId, name), onSuccess: refresh }),
    rename: useMutation({
      mutationFn: ({ id, name }: { id: number; name: string }) => overlayThemesAdminApi.renameSponsor(themeId, id, name),
      onSuccess: refresh,
    }),
    remove: useMutation({ mutationFn: (id: number) => overlayThemesAdminApi.deleteSponsor(themeId, id), onSuccess: refresh }),
    order: useMutation({ mutationFn: (ids: number[]) => overlayThemesAdminApi.orderSponsors(themeId, ids), onSuccess: refresh }),
    logo: useMutation({
      mutationFn: ({ id, file }: { id: number; file: File }) => overlayThemesAdminApi.uploadSponsorLogo(themeId, id, file),
      onSuccess: refresh,
    }),
  };
}
