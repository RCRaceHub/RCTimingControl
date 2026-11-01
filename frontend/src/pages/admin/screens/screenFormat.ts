import type { Screen, ScreenActionResult, ScreenConnection } from '@/lib/screensApi';

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
