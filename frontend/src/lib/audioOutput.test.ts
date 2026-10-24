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
import { listenForCues, resetPresence, routeCue, setThisDevice, setThisOutput, speechPieces } from './audioOutput';
import { LOCAL_OUTPUT_ID, resetRoutingCache, saveRouting } from './audioRouting';

/** An output page for this id says it is open. */
const emit = (data: unknown) => handlers.forEach((h) => h({ data }));
const open = (id: string) => emit({ presence: id });

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
    resetPresence();
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
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'onPace' });
    open('rostrum');
    posted.length = 0;
    const ms = routeCue('startHorn', 0.8, { kind: 'horn', horn: 'start' });
    expect(sounds.playStartHorn).not.toHaveBeenCalled();
    expect(ms).toBe(1200);
    expect(posted).toEqual([{ outputs: ['rostrum'], volume: 0.8, cue: { kind: 'horn', horn: 'start' } }]);
  });

  it('plays here and sends to the other outputs when both are ticked', () => {
    routing({ raceCalls: [LOCAL_OUTPUT_ID, 'rostrum'] });
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'onPace' });
    open('rostrum');
    posted.length = 0;
    routeCue('raceCalls', 0.5, { kind: 'speech', text: 'Race started.' });
    expect(speakWithBrowser).toHaveBeenCalledWith('Race started.', 0.5);
    expect(posted).toHaveLength(1);
  });

  it('plays nothing, and reports no horn, when the sound is ticked nowhere', () => {
    routing({ startHorn: [], lapBeep: [] });
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'faster' });
    expect(sounds.playLapBeep).not.toHaveBeenCalled();
    expect(routeCue('startHorn', 1, { kind: 'horn', horn: 'start' })).toBe(0);
    expect(posted).toEqual([]);
  });

  it('plays here instead when the output page it was sent to is not open', () => {
    routing({ startHorn: ['rostrum'] });
    // No output page has said it is open
    expect(routeCue('startHorn', 0.8, { kind: 'horn', horn: 'start' })).toBe(1200);
    expect(sounds.playStartHorn).toHaveBeenCalledOnce();
    expect(posted).toEqual([]);
  });

  it('pans an output page by its side and picks up its sound card', () => {
    routing({});
    setThisOutput('rostrum');
    setThisDevice('card-2');
    expect(sounds.setOutputTarget).toHaveBeenLastCalledWith({ deviceId: 'card-2', pan: -1 });
  });

  it('splits a long spoken line into pieces the speech preview takes', () => {
    const line = `Running order: ${Array.from({ length: 12 }, (_, i) => `${i + 1}, Driver Number${i}`).join('. ')}.`;
    const pieces = speechPieces(line);
    expect(pieces.length).toBeGreaterThan(1);
    expect(pieces.every((p) => p.length <= 100)).toBe(true);
    expect(pieces.join(' ').replace(/\s+/g, ' ')).toBe(line);
    expect(speechPieces('Race started.')).toEqual(['Race started.']);
  });

  it('gives an output page only the cues meant for it', () => {
    setThisOutput('rostrum');
    const heard = vi.fn();
    listenForCues(heard);
    const cue = { kind: 'speech', text: 'Hi' };
    emit({ outputs: ['pits'], volume: 1, cue });
    emit({ outputs: ['rostrum'], volume: 0.4, cue });
    expect(heard).toHaveBeenCalledOnce();
    expect(heard).toHaveBeenCalledWith(cue, 0.4);
  });
});
