/**
 * Horns and ticks for the race, made with the browser's audio context so there are no sound files to ship.
 * A club can replace either horn with its own sound file. Each horn returns how long it plays, in milliseconds,
 * so a spoken line can follow it.
 */

/** The club's own horn sound files, with how long each plays; a horn with none uses the built-in one. */
export interface HornSounds {
  startUrl?: string | null;
  startMs?: number | null;
  finishUrl?: string | null;
  finishMs?: number | null;
  tickUrl?: string | null;
  tickMs?: number | null;
  lapBeepUrl?: string | null;
  lapBeepMs?: number | null;
  /** Own files for a lap faster or slower than the driver's best; the lap beep above is for one on pace. */
  fasterBeepUrl?: string | null;
  fasterBeepMs?: number | null;
  slowerBeepUrl?: string | null;
  slowerBeepMs?: number | null;
}

/** How a lap compares with the driver's best before it. */
export type LapPace = 'faster' | 'onPace' | 'slower';

/** How long the start horn sounds. */
export const START_HORN_MS = 1200;
/** How long the finish horn sounds. */
export const FINISH_HORN_MS = 1800;

type Blast = { at: number; length: number; hz: number };

const START_BLASTS: Blast[] = [{ at: 0, length: 1.0, hz: 440 }];
const FINISH_BLASTS: Blast[] = [
  { at: 0, length: 0.4, hz: 440 },
  { at: 0.55, length: 0.4, hz: 440 },
  { at: 1.1, length: 0.6, hz: 440 },
];

/**
 * Where this tab's sound comes out: a sound card (its device id; the browser's default when null) and a stereo
 * pan from -1 (left only) to 1 (right only). Set by an audio output page (#478); race control uses the default.
 */
export interface OutputTarget {
  deviceId: string | null;
  pan: number;
}

let target: OutputTarget = { deviceId: null, pan: 0 };

type SinkContext = AudioContext & { setSinkId?: (id: string) => Promise<void> };
type SinkAudio = HTMLAudioElement & { setSinkId?: (id: string) => Promise<void> };

/** Sends this tab's sound to the sound card and side given; a sound already playing is not moved. */
export function setOutputTarget(next: OutputTarget) {
  target = next;
  if (shared && shared.state !== 'closed') applySink(shared);
}

/** The target this tab is sending sound to. */
export function getOutputTarget(): OutputTarget {
  return target;
}

function applySink(ctx: AudioContext) {
  // setSinkId needs Chrome or Edge 110 or later, and a page on localhost or HTTPS
  (ctx as SinkContext).setSinkId?.(target.deviceId ?? '').catch((e: unknown) => {
    console.warn('Could not use the chosen sound card:', e);
  });
}

/** Joins a source to the speakers through the target's pan. */
function connectOut(ctx: AudioContext, node: AudioNode) {
  if (target.pan !== 0 && ctx.createStereoPanner) {
    const panner = ctx.createStereoPanner();
    panner.pan.value = Math.min(1, Math.max(-1, target.pan));
    node.connect(panner);
    panner.connect(ctx.destination);
  } else {
    node.connect(ctx.destination);
  }
}

// One context for every sound: browsers cap how many can be open, and a start horn that comes from a
// server message, not a click, may find it suspended until it is resumed
let shared: AudioContext | null = null;

const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());

function context(): AudioContext {
  if (!shared || shared.state === 'closed') {
    shared = new AudioContext();
    shared.onstatechange = notify;
    if (target.deviceId) applySink(shared);
    notify();
  }
  if (shared.state !== 'running') void shared.resume().catch(() => undefined);
  return shared;
}

/**
 * Whether the browser is holding the page's sound back, as its autoplay rule does until the page has had a click
 * or key press. False before the first attempt to start audio, and where the browser has no audio at all.
 */
export function audioBlocked(): boolean {
  return shared != null && shared.state !== 'running';
}

/** Calls the listener whenever the audio context is created or changes state; returns the way to stop. */
export function subscribeAudioState(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Starts the audio context, on page load and again from a click or key press, which the browser then lets sound. */
export function unlockAudio() {
  if (typeof AudioContext === 'undefined') return;
  try {
    context();
  } catch (e) {
    console.warn('Audio unlock failed:', e);
  }
}

function play(blasts: Blast[], volume: number, wave: OscillatorType) {
  try {
    const ctx = context();
    const gain = ctx.createGain();
    gain.gain.value = Math.min(1, Math.max(0, volume)) * 0.6;
    connectOut(ctx, gain);
    for (const { at, length, hz } of blasts) {
      const osc = ctx.createOscillator();
      osc.type = wave;
      osc.frequency.value = hz;
      osc.connect(gain);
      osc.start(ctx.currentTime + at);
      osc.stop(ctx.currentTime + at + length);
      osc.onended = () => osc.disconnect();
    }
  } catch (e) {
    console.warn('Horn failed:', e);
  }
}

/** How long the start horn plays: the club's sound if it has one. */
export function startHornMs(sounds?: HornSounds | null): number {
  return sounds?.startUrl && sounds.startMs ? sounds.startMs : START_HORN_MS;
}

/** How long the finish horn plays: the club's sound if it has one. */
export function finishHornMs(sounds?: HornSounds | null): number {
  return sounds?.finishUrl && sounds.finishMs ? sounds.finishMs : FINISH_HORN_MS;
}

/**
 * Plays an audio element on this tab's target. A pan other than centre runs the element through the audio
 * context, which a sound card choice does not need. {@code onFail} runs if the element cannot play.
 */
export function playElement(audio: HTMLAudioElement, volume: number, onFail: (e?: unknown) => void) {
  // A file that will not play can fail twice (the error event and the rejected play): report it once
  let failed = false;
  const fail = (e?: unknown) => {
    if (failed) return;
    failed = true;
    onFail(e);
  };
  audio.volume = Math.min(1, Math.max(0, volume));
  audio.onerror = () => fail();
  const sink = (audio as SinkAudio).setSinkId;
  const ready = target.deviceId && sink ? sink.call(audio, target.deviceId).catch(() => undefined) : Promise.resolve();
  void ready.then(() => {
    if (target.pan !== 0) {
      try {
        const ctx = context();
        connectOut(ctx, ctx.createMediaElementSource(audio));
      } catch (e) {
        console.warn('Could not pan the sound:', e);
      }
    }
    return audio.play();
  }).catch(fail);
}

/** Plays the club's sound file; if the browser can't, sounds the built-in horn instead. */
function playFile(url: string, volume: number, builtIn: () => void) {
  // A file that will not play can fail twice (the error event and the rejected play): sound the horn once
  let fellBack = false;
  const fallBack = (e?: unknown) => {
    if (fellBack) return;
    fellBack = true;
    console.warn('Horn sound failed:', e);
    builtIn();
  };
  try {
    playElement(new Audio(url), volume, fallBack);
  } catch (e) {
    fallBack(e);
  }
}

/** One long blast, as a race starts; the club's own sound if it has uploaded one. */
export function playStartHorn(volume: number, sounds?: HornSounds | null): number {
  const builtIn = () => play(START_BLASTS, volume, 'sawtooth');
  if (sounds?.startUrl && sounds.startMs) playFile(sounds.startUrl, volume, builtIn);
  else builtIn();
  return startHornMs(sounds);
}

/** Two short blasts and a long one, as a race finishes; the club's own sound if it has uploaded one. */
export function playFinishHorn(volume: number, sounds?: HornSounds | null): number {
  const builtIn = () => play(FINISH_BLASTS, volume, 'sawtooth');
  if (sounds?.finishUrl && sounds.finishMs) playFile(sounds.finishUrl, volume, builtIn);
  else builtIn();
  return finishHornMs(sounds);
}

/** The lap beep's tone: its pitch, and its length in milliseconds. */
export interface BeepTone {
  hz?: number;
  ms?: number;
}

/** The club's file for a lap at this pace; a faster or slower lap with none of its own uses the lap beep file. */
function lapBeepFile(pace: LapPace, sounds?: HornSounds | null): { url: string; ms: number } | null {
  const picks = {
    faster: { url: sounds?.fasterBeepUrl, ms: sounds?.fasterBeepMs },
    slower: { url: sounds?.slowerBeepUrl, ms: sounds?.slowerBeepMs },
    onPace: { url: sounds?.lapBeepUrl, ms: sounds?.lapBeepMs },
  };
  for (const pick of pace === 'onPace' ? [picks.onPace] : [picks[pace], picks.onPace]) {
    if (pick.url && pick.ms) return { url: pick.url, ms: pick.ms };
  }
  return null;
}

/**
 * The beep for a lap: an octave higher when faster than the driver's best and an octave lower when slower; the
 * club's own sound for that pace if it has one.
 */
export function playLapBeep(volume: number, pace: LapPace, tone?: BeepTone, sounds?: HornSounds | null) {
  const builtIn = () => {
    const hz = tone?.hz ?? 440;
    const pitch = pace === 'faster' ? hz * 2 : pace === 'slower' ? hz / 2 : hz;
    play([{ at: 0, length: (tone?.ms ?? 200) / 1000, hz: pitch }], volume, 'sine');
  };
  const own = lapBeepFile(pace, sounds);
  if (own) playFile(own.url, volume, builtIn);
  else builtIn();
}

/** A short tick for the start countdown and the end of a timed race; the last one is higher. */
export function playTick(volume: number, last = false, sounds?: HornSounds | null) {
  const builtIn = () => play([{ at: 0, length: last ? 0.4 : 0.15, hz: last ? 1040 : 780 }], volume, 'sine');
  if (sounds?.tickUrl && sounds.tickMs) playFile(sounds.tickUrl, volume, builtIn);
  else builtIn();
}
