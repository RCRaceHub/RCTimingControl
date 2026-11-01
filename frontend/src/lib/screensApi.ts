import api from './api';

// The screen manager (#708): EventScreenDisplay viewers (Raspberry Pi screens) on the venue network, their playlists
// and the commands sent to them. Everything is for signed-in officials, so it goes through the signed-in client.

export type ScreenConnection = 'ONLINE' | 'UNREACHABLE' | 'OFFLINE' | 'UNKNOWN';

/** What is on a screen now, from its last status. Times are the screen's clock in Unix seconds. */
export type ScreenLive = {
  state: 'idle' | 'playing' | 'paused' | 'override' | string;
  paused: boolean;
  time: number | null;
  index: number | null;
  playlistLength: number | null;
  playlistRevision: number | null;
  playlistSource: string | null;
  currentUrl: string | null;
  currentLabel: string | null;
  currentEndsAt: number | null;
  overrideUrl: string | null;
  overrideUntil: number | null;
  pageReachable: boolean | null;
  browserRunning: boolean;
  browserRestarts: number | null;
  browserLastError: string | null;
  /** The full-screen message that is up, if any (agent 0.6.0+). */
  message: ScreenMessageLive | null;
  /** The banner that is up, if any (agent 0.6.0+). */
  banner: ScreenBannerLive | null;
};

/** A message as the screen reports it. Times are the screen's clock in Unix seconds; `until` is null when open-ended. */
export type ScreenMessageLive = {
  style: MessageStyle | string;
  title: string;
  text: string | null;
  level: MessageLevel | string;
  since: number | null;
  until: number | null;
};

/** A banner as the screen reports it. `visible` is false when the screen cannot draw it over the page now showing. */
export type ScreenBannerLive = {
  text: string;
  level: MessageLevel | string;
  since: number | null;
  until: number | null;
  visible: boolean;
};

export type MessageStyle = 'hold' | 'announce';
export type MessageLevel = 'info' | 'warning' | 'urgent';

export type Screen = {
  id: number;
  screenId: string;
  name: string;
  host: string;
  port: number;
  agentVersion: string | null;
  apiVersion: string | null;
  hasToken: boolean;
  playlistId: number | null;
  playlistName: string | null;
  pushedRevision: number | null;
  pushedSource: string | null;
  pushedAt: string | null;
  lastSeenAt: string | null;
  connection: ScreenConnection;
  error: string | null;
  statusAt: string | null;
  clockSkewSeconds: number | null;
  drifted: boolean;
  warnings: string[];
  /** What the screen's agent offers (`message`, `banner`, ...), or null until it has been read. */
  features: string[] | null;
  live: ScreenLive | null;
};

/** A screen heard on the network that has not been added yet. */
export type FoundScreen = {
  screenId: string;
  name: string;
  host: string;
  port: number;
  agentVersion: string | null;
  authRequired: boolean;
  seenAt: string;
};

export type ScreenList = { discoveryEnabled: boolean; screens: Screen[]; found: FoundScreen[] };

/** How one screen took a command sent to a group. */
export type ScreenActionResult = { id: number; screenId: string; name: string; ok: boolean; error: string | null };

export type PlaylistItem = { url: string; duration: number | null; label: string | null; id: string | null };

export type ScreenPlaylist = { id: number; name: string; defaultDuration: number; items: PlaylistItem[] };
export type ScreenPlaylistInput = Omit<ScreenPlaylist, 'id'>;

/** A page of this app, from its site root, and the addresses the screens can reach the app at. */
export type BoardPages = { baseUrls: string[]; pages: { label: string; path: string }[] };

export type ControlAction = 'next' | 'previous' | 'goto' | 'pause' | 'resume' | 'reload';

/** A message to put over the whole screen. `seconds` null means until cleared. */
export type ScreenMessageInput = {
  title: string;
  text: string | null;
  style: MessageStyle;
  level: MessageLevel;
  seconds: number | null;
};

/** A message saved under a name. */
export type MessagePreset = { name: string; message: ScreenMessageInput };

/** The two opt-in hooks from the event flow (both off by default); they go to every screen. */
export type MessageHooks = {
  holdAfterQualifying: boolean;
  holdMessage: ScreenMessageInput;
  announceFinals: boolean;
  finalsPlaylistId: number | null;
  finalsMessage: ScreenMessageInput;
};

export type MessageSettings = { presets: MessagePreset[]; hooks: MessageHooks };

/** How "Finals are up" went, one list per step. */
export type FinalsUpResult = { playlist: ScreenActionResult[]; message: ScreenActionResult[] };

/** The longest a message title, and a message text or banner, can be. */
export const MAX_TITLE = 120;
export const MAX_TEXT = 500;

/** A blank playlist for the editor to start from. */
export const NEW_PLAYLIST: ScreenPlaylistInput = { name: '', defaultDuration: 30, items: [] };

/** The page's full address: a base such as `http://192.168.1.5:8080/` joined to a path such as `/boards/results`. */
export function pageUrl(base: string, path: string): string {
  return base.replace(/\/+$/, '') + (path.startsWith('/') ? path : `/${path}`);
}

export const screensApi = {
  list: () => api.get<ScreenList>('/api/v1/admin/screens').then(r => r.data),
  boardPages: () => api.get<BoardPages>('/api/v1/admin/screens/board-pages').then(r => r.data),
  add: (body: { host: string; port: number | null; token?: string }) =>
    api.post<Screen>('/api/v1/admin/screens', body).then(r => r.data),
  scan: () => api.post('/api/v1/admin/screens/scan'),
  remove: (id: number) => api.delete(`/api/v1/admin/screens/${id}`),
  setToken: (screenIds: number[], token: string) => api.put('/api/v1/admin/screens/token', { screenIds, token }),
  push: (playlistId: number, screenIds: number[]) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/push', { playlistId, screenIds }).then(r => r.data),
  repush: (id: number) => api.post<ScreenActionResult[]>(`/api/v1/admin/screens/${id}/repush`).then(r => r.data),
  control: (action: ControlAction, screenIds: number[], page?: number) =>
    api.post<ScreenActionResult[]>(`/api/v1/admin/screens/control/${action}`, { screenIds, page: page ?? null })
      .then(r => r.data),
  show: (screenIds: number[], url: string, seconds: number | null, label: string) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/show', { screenIds, url, seconds, label: label || null })
      .then(r => r.data),
  clearShow: (screenIds: number[]) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/show/clear', { screenIds }).then(r => r.data),
  sendMessage: (screenIds: number[], message: ScreenMessageInput) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/message', { screenIds, ...message }).then(r => r.data),
  clearMessage: (screenIds: number[]) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/message/clear', { screenIds }).then(r => r.data),
  sendBanner: (screenIds: number[], banner: { text: string; level: MessageLevel; seconds: number | null }) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/banner', { screenIds, ...banner }).then(r => r.data),
  clearBanner: (screenIds: number[]) =>
    api.post<ScreenActionResult[]>('/api/v1/admin/screens/banner/clear', { screenIds }).then(r => r.data),
  finalsUp: (body: { screenIds: number[]; playlistId: number | null; title: string; text: string | null;
    level: MessageLevel; seconds: number | null }) =>
    api.post<FinalsUpResult>('/api/v1/admin/screens/finals-up', body).then(r => r.data),
  messageSettings: () => api.get<MessageSettings>('/api/v1/admin/screens/message-settings').then(r => r.data),
  saveMessageSettings: (settings: MessageSettings) =>
    api.put<MessageSettings>('/api/v1/admin/screens/message-settings', settings).then(r => r.data),
};

export const screenPlaylistsApi = {
  list: () => api.get<ScreenPlaylist[]>('/api/v1/admin/screen-playlists').then(r => r.data),
  create: (body: ScreenPlaylistInput) =>
    api.post<ScreenPlaylist>('/api/v1/admin/screen-playlists', body).then(r => r.data),
  update: (id: number, body: ScreenPlaylistInput) =>
    api.put<ScreenPlaylist>(`/api/v1/admin/screen-playlists/${id}`, body).then(r => r.data),
  delete: (id: number) => api.delete(`/api/v1/admin/screen-playlists/${id}`),
};
