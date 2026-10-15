import { describe, expect, it, vi } from 'vitest';
import { FINISH_HORN_MS, START_HORN_MS, finishHornMs, startHornMs } from './sounds';

describe('horn lengths', () => {
  it('are the built-in lengths until the club has its own sound', () => {
    expect(startHornMs(undefined)).toBe(START_HORN_MS);
    expect(startHornMs({})).toBe(START_HORN_MS);
    expect(finishHornMs({ startUrl: '/storage/a.wav', startMs: 900 })).toBe(FINISH_HORN_MS);
  });

  it("are the club's sound lengths when it has uploaded them", () => {
    const sounds = { startUrl: '/storage/horns/start-1.wav', startMs: 900, finishUrl: '/storage/horns/finish-1.mp3', finishMs: 2500 };
    expect(startHornMs(sounds)).toBe(900);
    expect(finishHornMs(sounds)).toBe(2500);
  });
});

describe('lap beep files', () => {
  it('uses the lap beep file for a faster or slower lap with none of its own', async () => {
    const { playLapBeep } = await import('./sounds');
    const played: string[] = [];
    vi.stubGlobal('Audio', class { constructor(url: string) { played.push(url); } volume = 1; play() { return Promise.resolve(); } });
    const sounds = { lapBeepUrl: '/on.wav', lapBeepMs: 200, slowerBeepUrl: '/slow.wav', slowerBeepMs: 200 };

    playLapBeep(0.5, 'faster', undefined, sounds);
    playLapBeep(0.5, 'slower', undefined, sounds);
    playLapBeep(0.5, 'onPace', undefined, sounds);

    expect(played).toEqual(['/on.wav', '/slow.wav', '/on.wav']);
    vi.unstubAllGlobals();
  });
});
