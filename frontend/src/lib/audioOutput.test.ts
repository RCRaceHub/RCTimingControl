import { beforeEach, describe, expect, it, vi } from 'vitest';

const sounds = vi.hoisted(() => ({
  playStartHorn: vi.fn(() => 1200),
  playFinishHorn: vi.fn(() => 1800),
  playTick: vi.fn(),
  playLapBeep: vi.fn(),
  playElement: vi.fn(),
  setOutputTarget: vi.fn(),
  startHornMs: vi.fn(() => 1200),
  finishHornMs: vi.fn(() => 1800),
}));
vi.mock('@/lib/sounds', () => sounds);
vi.mock('@/lib/speech', () => ({ speakWithBrowser: vi.fn() }));
vi.mock('@/lib/adminApi', () => ({ adminApi: { competitors: { previewSpeech: vi.fn() } } }));

const posted: unknown[] = [];
const handlers: Array<(e: { data: unknown }) => void> = [];
class FakeChannel {
  postMessage(message: unknown) {
    posted.push(message);
  }
  addEventListener(_: string, handler: (e: { data: unknown }) => void) {
    handlers.push(handler);
  }
  removeEventListener() {}
}
vi.stubGlobal('BroadcastChannel', FakeChannel);

import { speakWithBrowser } from '@/lib/speech';
import { listenForCues, routeCue, setThisDevice, setThisOutput } from './audioOutput';
import { LOCAL_OUTPUT_ID, resetRoutingCache, saveRouting } from './audioRouting';

const routing = (routes: Record<string, string[]>) => saveRouting({
  outputs: [
    { id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'both' },
    { id: 'rostrum', name: 'Rostrum', pan: 'left' },
  ],
  routes,
});

describe('routeCue', () => {
  beforeEach(() => {
    localStorage.clear();
    resetRoutingCache();
    posted.length = 0;
    handlers.length = 0;
    vi.clearAllMocks();
    setThisOutput(LOCAL_OUTPUT_ID);
  });

  it('plays here and tells no one when the sound is not routed', () => {
    const ms = routeCue('startHorn', 0.8, { kind: 'horn', horn: 'start' });
    expect(sounds.playStartHorn).toHaveBeenCalledOnce();
    expect(ms).toBe(1200);
    expect(posted).toEqual([]);
  });

  it('sends a sound ticked only for another output there, and still reports a horn\'s length', () => {
    routing({ startHorn: ['rostrum'] });
    const ms = routeCue('startHorn', 0.8, { kind: 'horn', horn: 'start' });
    expect(sounds.playStartHorn).not.toHaveBeenCalled();
    expect(ms).toBe(1200);
    expect(posted).toEqual([{ outputs: ['rostrum'], volume: 0.8, cue: { kind: 'horn', horn: 'start', } }]);
  });

  it('plays here and sends to the other outputs when both are ticked', () => {
    routing({ raceCalls: [LOCAL_OUTPUT_ID, 'rostrum'] });
    routeCue('raceCalls', 0.5, { kind: 'speech', text: 'Race started.' });
    expect(speakWithBrowser).toHaveBeenCalledWith('Race started.', 0.5);
    expect(posted).toHaveLength(1);
  });

  it('plays nothing when the sound is ticked nowhere', () => {
    routing({ lapBeep: [] });
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'faster' });
    expect(sounds.playLapBeep).not.toHaveBeenCalled();
    expect(posted).toEqual([]);
  });

  it('pans an output page by its side and picks up its sound card', () => {
    routing({});
    setThisOutput('rostrum');
    setThisDevice('card-2');
    expect(sounds.setOutputTarget).toHaveBeenLastCalledWith({ deviceId: 'card-2', pan: -1 });
  });

  it('gives an output page only the cues meant for it', () => {
    setThisOutput('rostrum');
    const heard = vi.fn();
    listenForCues(heard);
    const cue = { kind: 'speech', text: 'Hi' };
    handlers[0]({ data: { outputs: ['pits'], volume: 1, cue } });
    handlers[0]({ data: { outputs: ['rostrum'], volume: 0.4, cue } });
    expect(heard).toHaveBeenCalledOnce();
    expect(heard).toHaveBeenCalledWith(cue, 0.4);
  });
});
