import { adminApi } from '@/lib/adminApi';
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
 * Sends each sound to the outputs the club chose for it (#478). The tab that works out a sound (race control)
 * plays it itself when the race control output is ticked, and passes it to every other tab of the same browser
 * over a BroadcastChannel; an audio output page that is open for a ticked output plays it on its own sound card
 * and side. Nothing here touches the server, so a sound is never held up by the network.
 */

/** One sound to play, in a form that can be passed between tabs. */
export type Cue =
  | { kind: 'horn'; horn: 'start' | 'finish' | 'tick' | 'tickLast'; sounds?: HornSounds | null }
  | { kind: 'lapBeep'; pace: LapPace; tone?: BeepTone; sounds?: HornSounds | null }
  | { kind: 'speech'; text: string }
  /** A clip the server made, or the text to say when it can't be played. */
  | { kind: 'clip'; url: string | null; text: string };

interface Envelope {
  outputs: string[];
  volume: number;
  cue: Cue;
}

const CHANNEL = 'rc-audio-cues';
let opened: BroadcastChannel | null | undefined;

/** The channel to the other tabs of this browser, opened on first use; null where the browser has none. */
function getChannel(): BroadcastChannel | null {
  if (opened === undefined) opened = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL) : null;
  return opened;
}

/** The output this tab is: race control unless the tab is an audio output page. */
let thisOutput = LOCAL_OUTPUT_ID;
let lastPan = NaN;

/** Makes this tab the given output; used by the audio output page. */
export function setThisOutput(id: string) {
  thisOutput = id;
  lastPan = NaN;
}

/** The output this tab plays for. */
export function getThisOutput(): string {
  return thisOutput;
}

/** Applies the pan the club gave this tab's output, unless a sound card choice already sets it. */
function applyPan(deviceId: string | null) {
  const def = loadRouting().outputs.find((o) => o.id === thisOutput);
  const pan = panValue(def?.pan ?? 'both');
  if (pan === lastPan) return;
  const untouched = Number.isNaN(lastPan);
  lastPan = pan;
  // Nothing to set on a tab that has never been panned or given a sound card
  if (untouched && pan === 0 && !deviceId) return;
  setOutputTarget({ deviceId, pan });
}

/** Whether to say the text with a clip made by the club's Piper voice, which can be sent to a sound card. */
function isRoutedElsewhere(): boolean {
  return thisOutput !== LOCAL_OUTPUT_ID;
}

/** Says text on this tab: the browser's voice on race control, the club's Piper voice on an output page. */
async function speakHere(text: string, volume: number) {
  if (!isRoutedElsewhere()) {
    speakWithBrowser(text, volume);
    return;
  }
  // The browser voice always uses the default output, so an output page asks Piper for a clip it can route
  try {
    const wav = await adminApi.competitors.previewSpeech(text);
    const objectUrl = URL.createObjectURL(wav);
    const audio = new Audio(objectUrl);
    audio.addEventListener('ended', () => URL.revokeObjectURL(objectUrl));
    playElement(audio, volume, () => {
      URL.revokeObjectURL(objectUrl);
      speakWithBrowser(text, volume);
    });
  } catch {
    speakWithBrowser(text, volume);
  }
}

/** Plays a cue on this tab. Returns how long a horn plays, in milliseconds, 0 for anything else. */
export function playCueHere(cue: Cue, volume: number): number {
  applyPan(currentDevice);
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
      void speakHere(cue.text, volume);
      return 0;
    case 'clip': {
      if (!cue.url) {
        void speakHere(cue.text, volume);
        return 0;
      }
      playElement(new Audio(cue.url), volume, () => void speakHere(cue.text, volume));
      return 0;
    }
  }
}

let currentDevice: string | null = null;

/** Sets the sound card this tab plays on (null for the browser's default). */
export function setThisDevice(deviceId: string | null) {
  currentDevice = deviceId;
  lastPan = NaN;
  applyPan(deviceId);
}

/**
 * Plays the cue on every output the club ticked for this sound: here if race control is one of them, and on the
 * output pages for the rest. Returns how long a horn plays so a spoken line can follow it.
 */
export function routeCue(sound: SoundRoute, volume: number, cue: Cue): number {
  const outputs = outputsFor(loadRouting(), sound);
  const others = outputs.filter((id) => id !== thisOutput);
  if (others.length > 0) getChannel()?.postMessage({ outputs: others, volume, cue } satisfies Envelope);
  if (outputs.includes(thisOutput)) return playCueHere(cue, volume);
  // Not played here, but a spoken line still waits as long as the horn does
  if (cue.kind === 'horn') return cue.horn === 'start' ? startHornMs(cue.sounds) : cue.horn === 'finish' ? finishHornMs(cue.sounds) : 0;
  return 0;
}

/** Sends a cue to one named output, for the test buttons on the routing grid. */
export function sendCueTo(outputId: string, volume: number, cue: Cue) {
  if (outputId === thisOutput) playCueHere(cue, volume);
  else getChannel()?.postMessage({ outputs: [outputId], volume, cue } satisfies Envelope);
}

/** Calls the listener for each cue meant for this tab's output; returns the way to stop. Used by an output page. */
export function listenForCues(onCue: (cue: Cue, volume: number) => void): () => void {
  const channel = getChannel();
  if (!channel) return () => undefined;
  const handler = (event: MessageEvent<Envelope>) => {
    const { outputs, cue, volume } = event.data ?? ({} as Envelope);
    if (Array.isArray(outputs) && outputs.includes(thisOutput) && cue) onCue(cue, volume);
  };
  channel.addEventListener('message', handler);
  return () => channel.removeEventListener('message', handler);
}
