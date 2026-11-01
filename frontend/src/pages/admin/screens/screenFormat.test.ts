import { describe, expect, it } from 'vitest';
import type { Screen, ScreenLive } from '@/lib/screensApi';
import {
  bannerBadge, levelColour, messageBadge, secondsToNext, showing, stateLabel, summariseResults, supports, timeLeft,
} from './screenFormat';

const live: ScreenLive = {
  state: 'playing', paused: false, time: 1000, index: 0, playlistLength: 2, playlistRevision: 4,
  playlistSource: 'rctc:playlist-1', currentUrl: 'http://a/', currentLabel: 'Results', currentEndsAt: 1020,
  overrideUrl: null, overrideUntil: null, pageReachable: true, browserRunning: true, browserRestarts: 0, browserLastError: null,
  message: null, banner: null,
};

function screen(over: Partial<ScreenLive> | null): Screen {
  return {
    id: 1, screenId: 's1', name: 'Pit', host: '10.0.0.5', port: 8080, agentVersion: '0.1', apiVersion: '1', hasToken: false,
    playlistId: null, playlistName: null, pushedRevision: null, pushedSource: null, pushedAt: null, lastSeenAt: null,
    connection: 'ONLINE', error: null, statusAt: null, clockSkewSeconds: null, drifted: false, warnings: [], features: null,
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

  it('writes the time left as minutes and seconds, or nothing for an open end', () => {
    expect(timeLeft(1045, 1000)).toBe('45 s');
    expect(timeLeft(1125, 1000)).toBe('2:05');
    expect(timeLeft(900, 1000)).toBe('0 s');
    expect(timeLeft(null, 1000)).toBeNull();
    expect(timeLeft(1100, null)).toBeNull();
  });

  it('badges a message with its style, title and time left, and marks an open-ended one', () => {
    expect(messageBadge(screen({}))).toBeNull();
    expect(messageBadge(screen({ message: { style: 'announce', title: 'Finals up', text: null, level: 'info', since: 990, until: 1030 } })))
      .toEqual({ text: 'Announcement: Finals up · 30 s left', openEnded: false });
    expect(messageBadge(screen({ message: { style: 'hold', title: 'Wait', text: null, level: 'info', since: 990, until: null } })))
      .toEqual({ text: 'Hold: Wait · until cleared', openEnded: true });
  });

  it('badges a banner and says when the screen cannot draw it', () => {
    expect(bannerBadge(screen({}))).toBeNull();
    expect(bannerBadge(screen({ banner: { text: 'Pits close', level: 'warning', since: 1, until: 1300, visible: false } })))
      .toEqual({ text: 'Banner: Pits close · 5:00 left', hidden: true, openEnded: false });
  });

  it('only rules out a feature the screen has said it lacks', () => {
    expect(supports(screen({}), 'message')).toBe(true);
    expect(supports({ ...screen({}), features: ['playlist'] }, 'message')).toBe(false);
    expect(supports({ ...screen({}), features: ['message', 'banner'] }, 'banner')).toBe(true);
  });

  it('paints each level in the viewer\'s own colour and falls back to info', () => {
    expect(levelColour('info')).toBe('#0b1d33');
    expect(levelColour('warning')).toBe('#3a2a00');
    expect(levelColour('urgent')).toBe('#4a0b0b');
    expect(levelColour('mystery')).toBe('#0b1d33');
  });
});
