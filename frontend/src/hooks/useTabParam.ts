import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * A tab's state kept in the page address (`?tab=name`), so a refresh, a bookmark or a link from the help or
 * another page lands on the same tab. A page nested inside another tabbed page passes its own `key`
 * so the two do not share one parameter. A missing or unknown value falls back to {@code fallback}. Changing tab
 * replaces the history entry, so the back button leaves the page rather than stepping through its tabs.
 */
export function useTabParam<T extends string>(
  valid: readonly T[],
  fallback: T,
  key = 'tab',
): [T, (tab: string) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get(key);
  const tab = valid.find(v => v === raw) ?? fallback;
  const setTab = useCallback(
    (next: string) => {
      setParams(
        prev => {
          const copy = new URLSearchParams(prev);
          copy.set(key, next);
          return copy;
        },
        { replace: true },
      );
    },
    [setParams, key],
  );
  return [tab, setTab];
}
