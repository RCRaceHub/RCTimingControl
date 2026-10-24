import { useSyncExternalStore } from 'react';

/** How often the lap bars move. A bar changes by a few per cent a tick on a typical lap, so more is wasted work. */
const TICK_MS = 250;

let now = Date.now();
let timer: ReturnType<typeof setInterval> | undefined;
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) {
    now = Date.now();
    timer = setInterval(() => {
      now = Date.now();
      listeners.forEach((l) => l());
    }, TICK_MS);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) clearInterval(timer);
  };
}

/**
 * This device's clock in UTC milliseconds, updated four times a second for as long as something is showing it.
 * One timer serves every subscriber, and only the components that call this re-render on a tick.
 */
export function useTick(): number {
  return useSyncExternalStore(subscribe, () => now, () => now);
}
