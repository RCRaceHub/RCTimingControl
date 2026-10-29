import api from './api';
import type { OverlayThemeDto, SponsorDto } from '@/lib/boardsApi';

/** What is sent to create or change a theme: its name and the design as a JSON object. */
export type OverlayThemeInput = { name: string; design: Record<string, unknown> };

/** The signed-in side of the saved overlay themes (ADMIN and RACE_DIRECTOR change them; the starters are BRCA and Club). */
export const overlayThemesAdminApi = {
  list: () => api.get<OverlayThemeDto[]>('/api/v1/admin/overlay-themes').then(r => r.data),
  create: (body: OverlayThemeInput) => api.post<OverlayThemeDto>('/api/v1/admin/overlay-themes', body).then(r => r.data),
  update: (id: number, body: OverlayThemeInput) =>
    api.put<OverlayThemeDto>(`/api/v1/admin/overlay-themes/${id}`, body).then(r => r.data),
  delete: (id: number) => api.delete(`/api/v1/admin/overlay-themes/${id}`),

  // The sponsors a theme owns (name, optional logo, display order); the overlay shows them instead of the event's.
  listSponsors: (themeId: number) =>
    api.get<SponsorDto[]>(`/api/v1/admin/overlay-themes/${themeId}/sponsors`).then(r => r.data),
  addSponsor: (themeId: number, name: string) =>
    api.post<SponsorDto>(`/api/v1/admin/overlay-themes/${themeId}/sponsors`, { name }).then(r => r.data),
  renameSponsor: (themeId: number, id: number, name: string) =>
    api.put<SponsorDto>(`/api/v1/admin/overlay-themes/${themeId}/sponsors/${id}`, { name }).then(r => r.data),
  deleteSponsor: (themeId: number, id: number) =>
    api.delete(`/api/v1/admin/overlay-themes/${themeId}/sponsors/${id}`).then(() => undefined),
  orderSponsors: (themeId: number, ids: number[]) =>
    api.put<SponsorDto[]>(`/api/v1/admin/overlay-themes/${themeId}/sponsors/order`, { ids }).then(r => r.data),
  uploadSponsorLogo: (themeId: number, id: number, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api
      .put<SponsorDto>(`/api/v1/admin/overlay-themes/${themeId}/sponsors/${id}/logo`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(r => r.data);
  },
};
