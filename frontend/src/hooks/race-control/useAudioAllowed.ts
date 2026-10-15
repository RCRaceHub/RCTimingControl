import { useEffect, useSyncExternalStore } from 'react';
import { audioBlocked, subscribeAudioState, unlockAudio } from '@/lib/sounds';

/**
 * Whether the browser lets this page sound the horns. The page tries to start its audio at once; if the browser
 * refuses, the first click or key press anywhere on the page does it, and until then `blocked` is true so
 * the screen can say that sound is waiting for a click.
 */
export function useAudioAllowed() {
  const blocked = useSyncExternalStore(subscribeAudioState, audioBlocked, () => false);

  useEffect(() => {
    unlockAudio();
    const events = ['pointerdown', 'click', 'keydown'];
    events.forEach((event) => window.addEventListener(event, unlockAudio));
    return () => events.forEach((event) => window.removeEventListener(event, unlockAudio));
  }, []);

  return { blocked, unlock: unlockAudio };
}
