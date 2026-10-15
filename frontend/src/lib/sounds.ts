/**
 * Horns and ticks for the race, made with the browser's audio context so there are no sound files to ship.
 * Each returns how long it plays, in milliseconds, so a spoken line can follow it.
 */

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

// One context for every sound: browsers cap how many can be open, and a start horn that comes from a
// server message, not a click, may find it suspended until it is resumed
let shared: AudioContext | null = null;

const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());

function context(): AudioContext {
  if (!shared || shared.state === 'closed') {
    shared = new AudioContext();
    shared.onstatechange = notify;
    notify();
  }
  if (shared.state === 'suspended') void shared.resume().catch(() => undefined);
  return shared;
}

/**
 * Whether this browser lets the page make sound: false until the page has had a click or key press, when the
 * browser's autoplay rule keeps the horn silent.
 */
export function audioAllowed(): boolean {
  return shared != null && shared.state === 'running';
}

export function subscribeAudioState(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Starts the audio context; called from a click, which the browser then lets sound. */
export function unlockAudio() {
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
    gain.connect(ctx.destination);
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

/** One long blast, as a race starts. */
export function playStartHorn(volume: number): number {
  play(START_BLASTS, volume, 'sawtooth');
  return START_HORN_MS;
}

/** Two short blasts and a long one, as a race finishes. */
export function playFinishHorn(volume: number): number {
  play(FINISH_BLASTS, volume, 'sawtooth');
  return FINISH_HORN_MS;
}

/** A short tick for the start countdown; the last one is higher. */
export function playTick(volume: number, last = false) {
  play([{ at: 0, length: last ? 0.4 : 0.15, hz: last ? 1040 : 780 }], volume, 'sine');
}
