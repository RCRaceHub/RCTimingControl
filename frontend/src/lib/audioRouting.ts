import { useSyncExternalStore } from 'react';

/** The sounds and calls a club can send to an output (#478), as the routing grid lists them. */
export const SOUND_ROUTES = [
  { id: 'startHorn', label: 'Start horn' },
  { id: 'finishHorn', label: 'Finish horn' },
  { id: 'tick', label: 'Ticks (start lead-in and last seconds)' },
  { id: 'lapBeep', label: 'Lap beeps' },
  { id: 'startLeadIn', label: 'Start lead-in call' },
  { id: 'raceCalls', label: 'Grid call, started, stopped, resumed, abandoned' },
  { id: 'stagger', label: 'Driver calls at a stagger start' },
  { id: 'countdown', label: 'Time remaining and time up' },
  { id: 'raceFinished', label: 'Race finished line' },
  { id: 'carFinish', label: 'Car finish calls' },
  { id: 'nextRace', label: 'Next race, marshals and time to next start' },
  { id: 'runningOrder', label: 'Running order' },
] as const;

export type SoundRoute = (typeof SOUND_ROUTES)[number]['id'];

/** The output for the race control tab itself, which every club has and which cannot be removed. */
export const LOCAL_OUTPUT_ID = 'race-control';

/** Which speakers an output uses on its sound card. */
export type OutputPan = 'both' | 'left' | 'right';

export interface AudioOutputDef {
  /** Letters, digits and hyphens: it is the output page's address. */
  id: string;
  name: string;
  pan: OutputPan;
}

/** The club's outputs and, for each sound, the ids of the outputs that play it. */
export interface AudioRouting {
  outputs: AudioOutputDef[];
  /** A sound with no entry plays on the race control output only. */
  routes: Partial<Record<SoundRoute, string[]>>;
}

export const DEFAULT_ROUTING: AudioRouting = {
  outputs: [{ id: LOCAL_OUTPUT_ID, name: 'Race control', pan: 'both' }],
  routes: {},
};

const STORAGE_KEY = 'rc-audio-routing';

/** The pan value the audio engine takes for an output. */
export function panValue(pan: OutputPan): number {
  return pan === 'left' ? -1 : pan === 'right' ? 1 : 0;
}

/** The ids of the outputs that play this sound. */
export function outputsFor(routing: AudioRouting, sound: SoundRoute): string[] {
  return routing.routes[sound] ?? [LOCAL_OUTPUT_ID];
}

/** The routing with a usable race control output and no route to an output that does not exist. */
export function normalise(raw: unknown): AudioRouting {
  const given = (raw ?? {}) as Partial<AudioRouting>;
  const outputs: AudioOutputDef[] = [];
  for (const o of Array.isArray(given.outputs) ? given.outputs : []) {
    if (!o || typeof o.id !== 'string' || !/^[a-z0-9-]{1,40}$/.test(o.id)) continue;
    if (outputs.some((existing) => existing.id === o.id)) continue;
    const pan: OutputPan = o.pan === 'left' || o.pan === 'right' ? o.pan : 'both';
    outputs.push({ id: o.id, name: typeof o.name === 'string' && o.name.trim() ? o.name.trim() : o.id, pan });
  }
  if (!outputs.some((o) => o.id === LOCAL_OUTPUT_ID)) outputs.unshift(DEFAULT_ROUTING.outputs[0]);
  const known = new Set(outputs.map((o) => o.id));
  const routes: AudioRouting['routes'] = {};
  for (const { id } of SOUND_ROUTES) {
    const picked = given.routes?.[id];
    if (Array.isArray(picked)) routes[id] = picked.filter((out) => typeof out === 'string' && known.has(out));
  }
  return { outputs, routes };
}

let cached: AudioRouting | null = null;
const listeners = new Set<() => void>();

/** The routing saved in this browser. It is per browser because a sound card belongs to the machine. */
export function loadRouting(): AudioRouting {
  if (cached) return cached;
  try {
    const text = localStorage.getItem(STORAGE_KEY);
    cached = text ? normalise(JSON.parse(text)) : DEFAULT_ROUTING;
  } catch {
    cached = DEFAULT_ROUTING;
  }
  return cached;
}

export function saveRouting(routing: AudioRouting) {
  cached = normalise(routing);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cached));
  } catch {
    // Storage can be refused (private windows): the routing then lasts until the page reloads
  }
  listeners.forEach((listener) => listener());
}

// Another tab of this browser saved the routing
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    // A null key is the whole storage being cleared
    if (event.key !== null && event.key !== STORAGE_KEY) return;
    cached = null;
    listeners.forEach((listener) => listener());
  });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The routing saved in this browser, kept up to date as it changes. */
export function useAudioRouting(): AudioRouting {
  return useSyncExternalStore(subscribe, loadRouting, () => DEFAULT_ROUTING);
}

/** A free output id for this name: letters, digits and hyphens only. */
export function outputIdFor(name: string, taken: string[]): string {
  const base = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 30) || 'output';
  let id = base;
  for (let n = 2; taken.includes(id); n++) id = `${base}-${n}`;
  return id;
}

/** Forgets what was loaded, for tests. */
export function resetRoutingCache() {
  cached = null;
}
