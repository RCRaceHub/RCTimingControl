import { useEffect } from 'react';

/**
 * Puts {@code data-contrast="high"} on the page while {@code enabled} (#591), which the state icons key off to draw
 * thicker outlined icons with a short text tag beside them. It is on the document element, not a wrapper, because
 * the icons sit in many components and a board or the overlay owns the whole page. Removed again on unmount.
 */
export function useHighContrast(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) return;
    const root = document.documentElement;
    root.setAttribute('data-contrast', 'high');
    return () => root.removeAttribute('data-contrast');
  }, [enabled]);
}
