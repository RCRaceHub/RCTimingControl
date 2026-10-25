import { useEffect, useState } from 'react';

/** Which gap the tower shows: to the leader (GAP), to the car ahead (INT), or the two taking turns. */
export type GapMode = 'leader' | 'int' | 'auto';

/** How long the gap column takes to fade out before the other gap fades in, in milliseconds. */
const GAP_FADE_MS = 250;

/**
 * The gap the tower shows now, and whether it is mid-fade (#529). A fixed mode is returned as is. In {@code auto}
 * the view flips between INT and GAP every {@code everySeconds} on a timer that depends on the mode and the period
 * only, so timing updates, which re-render the tower every few seconds, never restart it. The flip fades the column
 * out for {@link #GAP_FADE_MS}, swaps, then fades back in.
 */
export function useGapView(mode: GapMode, everySeconds: number): { view: 'leader' | 'int'; fading: boolean } {
  const [autoView, setAutoView] = useState<'leader' | 'int'>('int');
  const [fading, setFading] = useState(false);

  useEffect(() => {
    if (mode !== 'auto') return;
    let swap: ReturnType<typeof setTimeout> | undefined;
    const timer = setInterval(() => {
      setFading(true);
      swap = setTimeout(() => {
        setAutoView((v) => (v === 'int' ? 'leader' : 'int'));
        setFading(false);
      }, GAP_FADE_MS);
    }, everySeconds * 1000);
    return () => {
      clearInterval(timer);
      if (swap) clearTimeout(swap);
    };
  }, [mode, everySeconds]);

  return { view: mode === 'auto' ? autoView : mode, fading: mode === 'auto' && fading };
}
