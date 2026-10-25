import api from './api';

// Saved board views (#481): a named screen built from panels, opened at /boards/view/{slug}. The read is public like the
// other boards; the changes need an official, so they go through the signed-in client like the rest of Admin.

export type BoardPanelType = 'LIVE_TIMING' | 'LIVE_TOP' | 'NEXT_RACE' | 'MARSHALS' | 'LAST_RESULT' | 'DRIVER_CARDS';

export type BoardPanel = {
  type: BoardPanelType;
  /** How many places a LIVE_TOP panel shows; null for the other types. */
  top: number | null;
  /** How many of the view's columns the panel takes. */
  span: number;
};

export type BoardView = {
  id: number;
  slug: string;
  name: string;
  columns: number;
  panels: BoardPanel[];
};

export type BoardViewInput = Omit<BoardView, 'id'>;

/** A blank view for the editor to start from. */
export const NEW_BOARD_VIEW: BoardViewInput = {
  slug: '',
  name: '',
  columns: 2,
  panels: [{ type: 'LIVE_TIMING', top: null, span: 2 }],
};

/** What each panel type is called and does, for the editor. */
export const PANEL_TYPES: { type: BoardPanelType; label: string; help: string }[] = [
  { type: 'LIVE_TIMING', label: 'Live timing', help: 'The race on track with lap bars and gaps' },
  { type: 'LIVE_TOP', label: 'Live top table', help: 'The class on track as it would stand if the heat ended now' },
  { type: 'NEXT_RACE', label: 'Next race', help: 'The countdown, the drivers and the marshals for the next race' },
  { type: 'MARSHALS', label: 'Marshals', help: 'The drivers marshalling the race on track' },
  { type: 'LAST_RESULT', label: 'Last result', help: 'The result of the race that finished last' },
  { type: 'DRIVER_CARDS', label: 'Driver cards', help: 'Headshots, car numbers and driver sponsors of the race on track, for the commentator' },
];

/** The anonymous read of one view; a 404 means there is no view with that address name. */
export async function getBoardView(slug: string): Promise<BoardView> {
  const { data } = await api.get<BoardView>(`/api/v1/boards/views/${encodeURIComponent(slug)}`);
  return data;
}

export const boardViewsAdminApi = {
  list: () => api.get<BoardView[]>('/api/v1/admin/board-views').then(r => r.data),
  create: (body: BoardViewInput) => api.post<BoardView>('/api/v1/admin/board-views', body).then(r => r.data),
  update: (id: number, body: BoardViewInput) =>
    api.put<BoardView>(`/api/v1/admin/board-views/${id}`, body).then(r => r.data),
  delete: (id: number) => api.delete(`/api/v1/admin/board-views/${id}`),
};
