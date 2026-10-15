import { useEffect, useSyncExternalStore } from 'react';
import { audioAllowed, subscribeAudioState, unlockAudio } from '@/lib/sounds';

/**
 * Whether the browser lets this page sound the horns. The page tries to start its audio at once; if the browser
 * refuses, the first click or key press anywhere on the page does it, and until then {@code allowed} is false so
 * the screen can say that sound is waiting for a click.
 */
export function useAudioAllowed() {
  const allowed = useSyncExternalStore(subscribeAudioState, audioAllowed, () => false);

  useEffect(() => {
    unlockAudio();
    const unlock = () => unlockAudio();
    window.addEventListener('pointerdown', unlock);
    window.addEventListener('keydown', unlock);
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, []);

  return { allowed, unlock: unlockAudio };
}
