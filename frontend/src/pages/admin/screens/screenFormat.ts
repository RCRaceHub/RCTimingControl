import type { MessageLevel, Screen, ScreenActionResult, ScreenConnection } from '@/lib/screensApi';

/** What each connection state is called, and the theme token its badge uses. */
export const CONNECTION: Record<ScreenConnection, { label: string; className: string }> = {
  ONLINE: { label: 'Online', className: 'bg-success/15 text-success' },
  UNREACHABLE: { label: 'Not answering', className: 'bg-warning/15 text-warning' },
  OFFLINE: { label: 'Offline', className: 'bg-destructive/15 text-destructive' },
  UNKNOWN: { label: 'Checking', className: 'bg-muted text-muted-foreground' },
};

/** What a screen is doing, in a few words. */
export function stateLabel(screen: Screen): string {
  const live = screen.live;
  if (!live) return '';
  switch (live.state) {
    case 'playing': return 'Playing';
    case 'paused': return 'Paused';
    case 'override': return 'Temporary page';
    case 'idle': return 'Idle (no playlist)';
    default: return live.state;
  }
}

/** The page on screen: the temporary page if there is one, else the playlist item, as a label or address. */
export function showing(screen: Screen): string {
  const live = screen.live;
  if (!live) return '';
  if (live.overrideUrl) return live.overrideUrl;
  return live.currentLabel || live.currentUrl || '';
}

/**
 * Seconds until the screen changes page, as of its last status (the screen's own clock, so a wrong clock cannot skew
 * it), or null when the page stays. Never below zero.
 */
export function secondsToNext(screen: Screen): number | null {
  const live = screen.live;
  if (!live || live.time == null) return null;
  const end = live.overrideUrl ? live.overrideUntil : live.currentEndsAt;
  return end == null ? null : Math.max(0, Math.round(end - live.time));
}

/** One line for a toast from the results of a command to a group: all well, or who failed and why. */
export function summariseResults(results: ScreenActionResult[]): { ok: boolean; text: string } {
  const failed = results.filter(r => !r.ok);
  if (failed.length === 0) return { ok: true, text: '' };
  return { ok: false, text: failed.map(r => `${r.name}: ${r.error ?? 'failed'}`).join('; ') };
}

/**
 * The colours the viewer paints a message or banner in, so the preview matches what the screen draws: a dark panel
 * per level with a white title. These are the viewer's own fixed colours, not theme tokens.
 */
export const MESSAGE_COLOURS: Record<MessageLevel, string> = {
  info: '#0b1d33',
  warning: '#3a2a00',
  urgent: '#4a0b0b',
};

/** The panel colour for a level the screen reported, falling back to info for one this app does not know. */
export function levelColour(level: string): string {
  return MESSAGE_COLOURS[level as MessageLevel] ?? MESSAGE_COLOURS.info;
}

/** Whether the screen can show messages or banners: false only when its agent has said it lacks the feature. */
export function supports(screen: Screen, feature: 'message' | 'banner'): boolean {
  return screen.features == null || screen.features.includes(feature);
}

/** "2:05" or "45 s" left until a time on the screen's own clock, or null when there is no end. */
export function timeLeft(until: number | null, now: number | null): string | null {
  if (until == null || now == null) return null;
  const seconds = Math.max(0, Math.round(until - now));
  return seconds >= 60 ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : `${seconds} s`;
}

/** What the badge on a screen says about the message up on it, or null when none is. */
export function messageBadge(screen: Screen): { text: string; openEnded: boolean } | null {
  const message = screen.live?.message;
  if (!message) return null;
  const left = timeLeft(message.until, screen.live?.time ?? null);
  return {
    text: `${message.style === 'announce' ? 'Announcement' : 'Hold'}: ${message.title} · ${left ? `${left} left` : 'until cleared'}`,
    openEnded: message.until == null,
  };
}

/** What the badge on a screen says about the banner up on it, or null when none is; `hidden` when it cannot be drawn. */
export function bannerBadge(screen: Screen): { text: string; hidden: boolean; openEnded: boolean } | null {
  const banner = screen.live?.banner;
  if (!banner) return null;
  const left = timeLeft(banner.until, screen.live?.time ?? null);
  return {
    text: `Banner: ${banner.text} · ${left ? `${left} left` : 'until cleared'}`,
    hidden: !banner.visible,
    openEnded: banner.until == null,
  };
}
