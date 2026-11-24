import { beforeEach, describe, expect, it, vi } from 'vitest';

/** The side and card the audio engine was set to at the moment each sound was handed to it. */
const heard: Array<{ what: string; deviceId: string | null; pan: number }> = [];
const state = vi.hoisted(() => ({ target: { deviceId: null as string | null, pan: 0 } }));

const sounds = vi.hoisted(() => {
  const record = (what: string) => () => {
    heard.push({ what, ...state.target });
    return 1000;
  };
  return {
    playStartHorn: vi.fn(record('startHorn')),
    playFinishHorn: vi.fn(record('finishHorn')),
    playTick: vi.fn(record('tick')),
    playLapBeep: vi.fn(record('lapBeep')),
    playElement: vi.fn(record('clip')),
    setOutputTarget: vi.fn((t: { deviceId: string | null; pan: number }) => { state.target = t; }),
    startHornMs: vi.fn(() => 1200),
    finishHornMs: vi.fn(() => 1800),
  };
});
vi.mock('@/lib/sounds', () => sounds);
vi.mock('@/lib/speech', () => ({ speakWithBrowser: vi.fn() }));
const previewSpeech = vi.hoisted(() => vi.fn());
vi.mock('@/lib/adminApi', () => ({ adminApi: { competitors: { previewSpeech } } }));

const posted: Array<{ outputs: string[] }> = [];
const handlers: Array<(e: { data: unknown }) => void> = [];
class FakeChannel {
  postMessage(message: { outputs: string[] }) {
    posted.push(message);
  }
  addEventListener(_: string, handler: (e: { data: unknown }) => void) {
    handlers.push(handler);
  }
  removeEventListener() {}
}
vi.stubGlobal('BroadcastChannel', FakeChannel);

import { speakWithBrowser } from '@/lib/speech';
import { playCueHere, resetPresence, routeCue, setThisOutput } from './audioOutput';
import { resetDeviceCache, saveOutputDevice } from './audioDevice';
import { LOCAL_OUTPUT_ID, resetRoutingCache, saveRouting, type AudioRouting } from './audioRouting';

const open = (id: string) => handlers.forEach((h) => h({ data: { presence: id } }));
const CARD_ONE = { deviceId: 'card-one', label: 'Card one' };
const CARD_TWO = { deviceId: 'card-two', label: 'Card two' };

/** These scenarios are the worked examples in the user manual's audio section (#478). */
describe('audio routing scenarios from the user manual', () => {
  beforeEach(() => {
    localStorage.clear();
    resetDeviceCache();
    resetRoutingCache();
    resetPresence();
    posted.length = 0;
    heard.length = 0;
    state.target = { deviceId: null, pan: 0 };
    vi.clearAllMocks();
    setThisOutput(LOCAL_OUTPUT_ID);
  });

  it('1: everything on the left of one card, on any screen, without visiting the settings page', async () => {
    saveRouting({ outputs: [{ id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'left' }], routes: {} });
    saveOutputDevice(LOCAL_OUTPUT_ID, CARD_ONE);
    previewSpeech.mockRejectedValue(new Error('no piper'));

    routeCue('startHorn', 1, { kind: 'horn', horn: 'start' });
    routeCue('finishHorn', 1, { kind: 'horn', horn: 'finish' });
    routeCue('tick', 1, { kind: 'horn', horn: 'tick' });
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'faster' });
    routeCue('raceCalls', 1, { kind: 'clip', url: '/clip.wav', text: 'Race started.' });
    routeCue('raceCalls', 1, { kind: 'speech', text: 'Race started.' });

    expect(heard.map((h) => h.what)).toEqual(['startHorn', 'finishHorn', 'tick', 'lapBeep', 'clip']);
    for (const sound of heard) expect(sound).toMatchObject({ deviceId: 'card-one', pan: -1 });
    // A spoken line is made by Piper, never the browser voice, so it can follow the card and side
    await vi.waitFor(() => expect(previewSpeech).toHaveBeenCalledWith('Race started.'));
  });

  it('1: the choice is still there after a reload', () => {
    saveRouting({ outputs: [{ id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'left' }], routes: {} });
    saveOutputDevice(LOCAL_OUTPUT_ID, CARD_ONE);
    // A reload forgets everything held in memory but keeps what is in the browser's storage
    resetDeviceCache();
    resetRoutingCache();
    routeCue('startHorn', 1, { kind: 'horn', horn: 'start' });
    expect(heard).toEqual([{ what: 'startHorn', deviceId: 'card-one', pan: -1 }]);
  });

  it('2: announcements left on card one, horns right on card two', () => {
    const routing: AudioRouting = {
      outputs: [
        { id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'both' },
        { id: 'calls', name: 'Calls', pan: 'left' },
        { id: 'horns', name: 'Horns', pan: 'right' },
      ],
      routes: { raceCalls: ['calls'], startHorn: ['horns'], lapBeep: ['horns'] },
    };
    saveRouting(routing);
    saveOutputDevice('calls', CARD_ONE);
    saveOutputDevice('horns', CARD_TWO);
    open('calls');
    open('horns');

    routeCue('raceCalls', 1, { kind: 'speech', text: 'Race started.' });
    routeCue('startHorn', 1, { kind: 'horn', horn: 'start' });
    routeCue('lapBeep', 1, { kind: 'lapBeep', pace: 'onPace' });
    // Race control plays none of them itself, and each goes only to its own output
    expect(heard).toEqual([]);
    expect(speakWithBrowser).not.toHaveBeenCalled();
    expect(posted.map((p) => p.outputs)).toEqual([['calls'], ['horns'], ['horns']]);

    // Each output's page then plays on its own card and side
    setThisOutput('calls');
    playCueHere({ kind: 'horn', horn: 'tick' }, 1);
    setThisOutput('horns');
    playCueHere({ kind: 'horn', horn: 'start' }, 1);
    expect(heard).toEqual([
      { what: 'tick', deviceId: 'card-one', pan: -1 },
      { what: 'startHorn', deviceId: 'card-two', pan: 1 },
    ]);
  });

  it('3: a closed output page is played by race control on that output\'s side', () => {
    saveRouting({
      outputs: [
        { id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'both' },
        { id: 'calls', name: 'Calls', pan: 'left' },
        { id: 'horns', name: 'Horns', pan: 'right' },
      ],
      routes: { startHorn: ['horns'], tick: ['calls', 'horns'] },
    });
    routeCue('startHorn', 1, { kind: 'horn', horn: 'start' });
    expect(heard.at(-1)).toMatchObject({ what: 'startHorn', pan: 1 });
    // Closed outputs on different sides: both speakers, so no side is left out
    routeCue('tick', 1, { kind: 'horn', horn: 'tick' });
    expect(heard.at(-1)).toMatchObject({ what: 'tick', pan: 0 });
  });
});
