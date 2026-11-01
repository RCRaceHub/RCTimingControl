import { describe, expect, it } from 'vitest';
import type { Screen, ScreenLive } from '@/lib/screensApi';
import { secondsToNext, showing, stateLabel, summariseResults } from './screenFormat';

const live: ScreenLive = {
  state: 'playing', paused: false, time: 1000, index: 0, playlistLength: 2, playlistRevision: 4,
  playlistSource: 'rctc:playlist-1', currentUrl: 'http://a/', currentLabel: 'Results', currentEndsAt: 1020,
  overrideUrl: null, overrideUntil: null, pageReachable: true, browserRunning: true, browserRestarts: 0, browserLastError: null,
};

function screen(over: Partial<ScreenLive> | null): Screen {
  return {
    id: 1, screenId: 's1', name: 'Pit', host: '10.0.0.5', port: 8080, agentVersion: '0.1', apiVersion: '1', hasToken: false,
    playlistId: null, playlistName: null, pushedRevision: null, pushedSource: null, pushedAt: null, lastSeenAt: null,
    connection: 'ONLINE', error: null, statusAt: null, clockSkewSeconds: null, drifted: false, warnings: [],
    live: over === null ? null : { ...live, ...over },
  };
}

describe('screenFormat', () => {
  it('counts down to the next page on the screen\'s own clock', () => {
    expect(secondsToNext(screen({}))).toBe(20);
    expect(secondsToNext(screen({ time: 1030 }))).toBe(0);
    expect(secondsToNext(screen({ currentEndsAt: null }))).toBeNull();
    expect(secondsToNext(screen(null))).toBeNull();
  });

  it('counts down a temporary page to its own end', () => {
    expect(secondsToNext(screen({ overrideUrl: 'http://b/', overrideUntil: 1005 }))).toBe(5);
    expect(secondsToNext(screen({ overrideUrl: 'http://b/', overrideUntil: null }))).toBeNull();
  });

  it('names what is showing and what state it is in', () => {
    expect(showing(screen({}))).toBe('Results');
    expect(showing(screen({ currentLabel: null }))).toBe('http://a/');
    expect(showing(screen({ overrideUrl: 'http://b/' }))).toBe('http://b/');
    expect(stateLabel(screen({ state: 'override' }))).toBe('Temporary page');
    expect(stateLabel(screen({ state: 'idle' }))).toBe('Idle (no playlist)');
  });

  it('reports which screens refused a command and why', () => {
    expect(summariseResults([{ id: 1, screenId: 's1', name: 'Pit', ok: true, error: null }]).ok).toBe(true);
    const summary = summariseResults([
      { id: 1, screenId: 's1', name: 'Pit', ok: true, error: null },
      { id: 2, screenId: 's2', name: 'Bar', ok: false, error: 'Cannot reach the screen' },
    ]);
    expect(summary).toEqual({ ok: false, text: 'Bar: Cannot reach the screen' });
  });
});
