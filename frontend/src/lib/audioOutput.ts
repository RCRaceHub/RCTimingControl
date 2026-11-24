import { adminApi } from '@/lib/adminApi';
import { readOutputDevice } from '@/lib/audioDevice';
import { LOCAL_OUTPUT_ID, loadRouting, outputsFor, panValue, type SoundRoute } from '@/lib/audioRouting';
import { speakWithBrowser } from '@/lib/speech';
import {
  finishHornMs,
  playElement,
  playFinishHorn,
  playLapBeep,
  playStartHorn,
  playTick,
  setOutputTarget,
  startHornMs,
  type BeepTone,
  type HornSounds,
  type LapPace,
} from '@/lib/sounds';

/**
 * Sends each sound to the outputs the club ticked for it (#478). The tab that works out a sound (race control)
 * plays it itself when the race control output is ticked, and passes it to every other tab of the same browser
 * over a BroadcastChannel; an audio output page that is open for a ticked output plays it on its own sound card
 * and side. Nothing here touches the server, except an output page asking the club's Piper voice to make a
 * spoken line.
 */

/** One sound to play, in a form that can be passed between tabs. */
export type Cue =
  | { kind: 'horn'; horn: 'start' | 'finish' | 'tick' | 'tickLast'; sounds?: HornSounds | null }
  | { kind: 'lapBeep'; pace: LapPace; tone?: BeepTone; sounds?: HornSounds | null }
  | { kind: 'speech'; text: string }
  /** A clip the server made, or the text to say when it can't be played. */
  | { kind: 'clip'; url: string | null; text: string };

/** A cue for the output pages named in {@code outputs}. */
interface Envelope {
  outputs: string[];
  volume: number;
  cue: Cue;
}

/** Sent by an output page every few seconds so race control knows it is open. */
interface Presence {
  presence: string;
}

const CHANNEL = 'rc-audio-cues';
/** How often an output page says it is open. */
const PRESENCE_EVERY_MS = 2000;
/** An output page not heard from for this long is taken to be closed. */
const PRESENCE_LIFETIME_MS = 6000;
/** The most the server's speech preview takes in one request. */
const MAX_SPEECH_CHARS = 100;

let opened: BroadcastChannel | null | undefined;
const lastSeen = new Map<string, number>();
const presenceListeners = new Set<() => void>();

/** The output this tab is: race control unless the tab is an audio output page. */
let thisOutput = LOCAL_OUTPUT_ID;
/** The sound card and side last handed to the audio engine. */
let lastDevice: string | null = null;
let lastPan = NaN;

/** The channel to the other tabs of this browser, opened on first use; null where the browser has none. */
function getChannel(): BroadcastChannel | null {
  if (opened === undefined) {
    opened = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL) : null;
    opened?.addEventListener('message', (event: MessageEvent<Presence>) => {
      const id = event.data?.presence;
      if (typeof id !== 'string') return;
      lastSeen.set(id, Date.now());
      presenceListeners.forEach((listener) => listener());
    });
  }
  return opened;
}

/** Whether an output page for this output has said it is open lately. */
export function isOutputOpen(outputId: string): boolean {
  if (outputId === thisOutput) return true;
  const seen = lastSeen.get(outputId);
  return seen !== undefined && Date.now() - seen < PRESENCE_LIFETIME_MS;
}

/** Forgets which output pages were open, for tests. */
export function resetPresence() {
  lastSeen.clear();
}

/** Calls the listener when an output page says it is open; returns the way to stop. */
export function subscribePresence(listener: () => void): () => void {
  getChannel();
  presenceListeners.add(listener);
  return () => {
    presenceListeners.delete(listener);
  };
}

/** Makes this tab the given output and tells the other tabs it is open, until the returned function is called. */
export function setThisOutput(id: string): () => void {
  thisOutput = id;
  lastPan = NaN;
  lastDevice = null;
  if (id === LOCAL_OUTPUT_ID) return () => undefined;
  const announce = () => getChannel()?.postMessage({ presence: id } satisfies Presence);
  announce();
  const timer = setInterval(announce, PRESENCE_EVERY_MS);
  return () => clearInterval(timer);
}

/**
 * Hands the audio engine the sound card kept for this tab's output and the side the club gave it. Run before every
 * sound, so a sound card chosen on any screen of this browser applies at once, to the next sound whatever played it.
 */
export function applyOutputTarget() {
  const deviceId = readOutputDevice(thisOutput)?.deviceId ?? null;
  const def = loadRouting().outputs.find((o) => o.id === thisOutput);
  const pan = panValue(def?.pan ?? 'both');
  if (pan === lastPan && deviceId === lastDevice) return;
  const untouched = Number.isNaN(lastPan);
  lastPan = pan;
  lastDevice = deviceId;
  // Nothing to set on a tab that has never been panned or given a sound card
  if (untouched && pan === 0 && !deviceId) return;
  setOutputTarget({ deviceId, pan });
}

/** Splits a line at sentence ends, then commas, then spaces, into pieces the speech preview will take. */
export function speechPieces(text: string, max = MAX_SPEECH_CHARS): string[] {
  const pieces: string[] = [];
  let rest = text.trim();
  while (rest.length > max) {
    const window = rest.slice(0, max);
    const cut = Math.max(window.lastIndexOf('. '), window.lastIndexOf(', '), window.lastIndexOf(' '));
    const at = cut > 0 ? cut + 1 : max;
    pieces.push(rest.slice(0, at).trim());
    rest = rest.slice(at).trim();
  }
  if (rest) pieces.push(rest);
  return pieces;
}

/** Plays one piece with the club's Piper voice and resolves when it has ended (or could not play). */
async function playPiper(text: string, volume: number): Promise<void> {
  const wav = await adminApi.competitors.previewSpeech(text);
  const objectUrl = URL.createObjectURL(wav);
  await new Promise<void>((resolve, reject) => {
    const audio = new Audio(objectUrl);
    audio.addEventListener('ended', () => resolve());
    playElement(audio, volume, reject);
  }).finally(() => URL.revokeObjectURL(objectUrl));
}

/** Spoken lines wait for the one before, as the browser's own voice does. */
let speaking: Promise<void> = Promise.resolve();

/**
 * Says text on this tab, one line after another. The browser's own voice always comes out of the default sound
 * card and cannot be panned, so it is used only where nothing is chosen for this tab; otherwise the club's Piper
 * voice is played as audio the tab can route. A line Piper cannot make plays with the browser's voice.
 */
function speakHere(text: string, volume: number) {
  const routed = thisOutput !== LOCAL_OUTPUT_ID || lastDevice !== null || lastPan !== 0;
  if (!routed) {
    speakWithBrowser(text, volume);
    return;
  }
  speaking = speaking.then(async () => {
    try {
      for (const piece of speechPieces(text)) await playPiper(piece, volume);
    } catch {
      speakWithBrowser(text, volume);
    }
  });
}

/** Says a line on this tab's output, for a test button or a preview; the same route a race call takes. */
export function speakPreview(text: string, volume: number) {
  applyOutputTarget();
  speakHere(text, volume);
}

/**
 * Plays an audio element on this tab's output, for a preview that is not a race sound. {@code onFail} runs if it
 * cannot play.
 */
export function playPreview(audio: HTMLAudioElement, volume: number, onFail: (e?: unknown) => void) {
  applyOutputTarget();
  playElement(audio, volume, onFail);
}

/** Plays a cue on this tab. Returns how long a horn plays, in milliseconds, 0 for anything else. */
export function playCueHere(cue: Cue, volume: number): number {
  applyOutputTarget();
  switch (cue.kind) {
    case 'horn':
      if (cue.horn === 'start') return playStartHorn(volume, cue.sounds);
      if (cue.horn === 'finish') return playFinishHorn(volume, cue.sounds);
      playTick(volume, cue.horn === 'tickLast', cue.sounds);
      return 0;
    case 'lapBeep':
      playLapBeep(volume, cue.pace, cue.tone, cue.sounds);
      return 0;
    case 'speech':
      speakHere(cue.text, volume);
      return 0;
    case 'clip':
      if (cue.url) playElement(new Audio(cue.url), volume, () => speakHere(cue.text, volume));
      else speakHere(cue.text, volume);
      return 0;
    default:
      return 0;
  }
}

/** How long a horn cue plays, for a spoken line that follows it. */
function hornLength(cue: Cue): number {
  if (cue.kind !== 'horn') return 0;
  if (cue.horn === 'start') return startHornMs(cue.sounds);
  return cue.horn === 'finish' ? finishHornMs(cue.sounds) : 0;
}

/**
 * Plays the cue on every output the club ticked for this sound: here if race control is one of them, and on the
 * output pages for the rest. An output page that has not been heard from lately is taken to be closed, so its
 * share plays here instead of being lost. Returns how long a horn plays so a spoken line can follow it, 0 when
 * the sound plays nowhere.
 */
export function routeCue(sound: SoundRoute, volume: number, cue: Cue): number {
  const outputs = outputsFor(loadRouting(), sound);
  getChannel();
  const open = outputs.filter((id) => id !== thisOutput && isOutputOpen(id));
  const missing = outputs.some((id) => id !== thisOutput && !open.includes(id));
  if (open.length > 0) getChannel()?.postMessage({ outputs: open, volume, cue } satisfies Envelope);
  if (outputs.includes(thisOutput) || missing) return playCueHere(cue, volume) || hornLength(cue);
  return open.length > 0 ? hornLength(cue) : 0;
}

/** Sends a cue to one named output, for the test buttons on the routing grid. */
export function sendCueTo(outputId: string, volume: number, cue: Cue) {
  if (outputId === thisOutput) playCueHere(cue, volume);
  else getChannel()?.postMessage({ outputs: [outputId], volume, cue } satisfies Envelope);
}

const CUE_KINDS = ['horn', 'lapBeep', 'speech', 'clip'];

/** Calls the listener for each cue meant for this tab's output; returns the way to stop. Used by an output page. */
export function listenForCues(onCue: (cue: Cue, volume: number) => void): () => void {
  const channel = getChannel();
  if (!channel) return () => undefined;
  const handler = (event: MessageEvent<Envelope>) => {
    const { outputs, cue, volume } = event.data ?? ({} as Envelope);
    if (!Array.isArray(outputs) || !outputs.includes(thisOutput)) return;
    if (!cue || !CUE_KINDS.includes(cue.kind)) return;
    onCue(cue, Math.min(1, Math.max(0, Number(volume) || 0)));
  };
  channel.addEventListener('message', handler);
  return () => channel.removeEventListener('message', handler);
}
