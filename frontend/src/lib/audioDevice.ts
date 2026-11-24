import { useSyncExternalStore } from 'react';

/** A sound card as the browser names it; the id only means something in the browser that listed it. */
export interface SoundCard {
  deviceId: string;
  label: string;
}

const key = (outputId: string) => `rc-audio-output-device:${outputId}`;

const listeners = new Set<() => void>();
const cache = new Map<string, SoundCard | null>();

/** The sound card kept for an output in this browser, or null for the browser's default. */
export function readOutputDevice(outputId: string): SoundCard | null {
  if (cache.has(outputId)) return cache.get(outputId) ?? null;
  let found: SoundCard | null = null;
  try {
    const text = localStorage.getItem(key(outputId));
    const parsed = text ? JSON.parse(text) : null;
    if (parsed && typeof parsed.deviceId === 'string') {
      found = { deviceId: parsed.deviceId, label: typeof parsed.label === 'string' ? parsed.label : parsed.deviceId };
    }
  } catch {
    found = null;
  }
  cache.set(outputId, found);
  return found;
}

/**
 * Keeps the sound card chosen for an output (null for the browser's default) in this browser, so it is still
 * chosen after a reload and in every other tab of the browser, race control's included.
 */
export function saveOutputDevice(outputId: string, card: SoundCard | null) {
  cache.set(outputId, card);
  try {
    if (card) localStorage.setItem(key(outputId), JSON.stringify(card));
    else localStorage.removeItem(key(outputId));
  } catch {
    // Private windows can refuse storage; the choice then lasts until the page reloads
  }
  listeners.forEach((listener) => listener());
}

/** Calls the listener when any output's sound card changes, here or in another tab; returns the way to stop. */
export function subscribeOutputDevices(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// Another tab of this browser chose a sound card
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== null && !event.key.startsWith('rc-audio-output-device:')) return;
    cache.clear();
    listeners.forEach((listener) => listener());
  });
}

/** The sound card kept for an output, kept up to date as it changes. */
export function useOutputDevice(outputId: string): SoundCard | null {
  return useSyncExternalStore(subscribeOutputDevices, () => readOutputDevice(outputId), () => null);
}

/** Forgets what was read, for tests. */
export function resetDeviceCache() {
  cache.clear();
}
