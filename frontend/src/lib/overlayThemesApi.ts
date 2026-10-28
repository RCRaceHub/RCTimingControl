import api from './api';
import type { OverlayThemeDto } from '@/lib/boardsApi';

/** What is sent to create or change a theme: its name and the design as a JSON object. */
export type OverlayThemeInput = { name: string; design: Record<string, unknown> };

/** The signed-in side of the saved overlay themes (ADMIN and RACE_DIRECTOR change them; the starters are BRCA and Club). */
export const overlayThemesAdminApi = {
  list: () => api.get<OverlayThemeDto[]>('/api/v1/admin/overlay-themes').then(r => r.data),
  create: (body: OverlayThemeInput) => api.post<OverlayThemeDto>('/api/v1/admin/overlay-themes', body).then(r => r.data),
  update: (id: number, body: OverlayThemeInput) =>
    api.put<OverlayThemeDto>(`/api/v1/admin/overlay-themes/${id}`, body).then(r => r.data),
  delete: (id: number) => api.delete(`/api/v1/admin/overlay-themes/${id}`),
};
