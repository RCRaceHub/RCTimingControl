import { useCallback, useState } from 'react';

/** The parts of the race control page an official can hide, and what the menu calls them. */
export const COCKPIT_SECTIONS = [
  { id: 'autoRun', label: 'Auto run' },
  { id: 'audio', label: 'Audio settings' },
  { id: 'liveTiming', label: 'Live timing' },
  { id: 'lineups', label: 'Heat and final line-ups' },
] as const;

export type CockpitSectionId = (typeof COCKPIT_SECTIONS)[number]['id'];

const STORAGE_KEY = 'rc-cockpit-hidden';

function load(): CockpitSectionId[] {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    const known = COCKPIT_SECTIONS.map(s => s.id as string);
    return Array.isArray(raw) ? raw.filter((id): id is CockpitSectionId => known.includes(id)) : [];
  } catch {
    return [];
  }
}

function save(hidden: CockpitSectionId[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(hidden));
  } catch {
    // Private windows and blocked storage just forget the choice
  }
}

/**
 * Which sections of the race control page this browser has hidden (#481), kept in local storage so a second laptop
 * or a tablet can show a smaller page. Nothing is hidden by default.
 */
export function useCockpitSections() {
  const [hidden, setHidden] = useState<CockpitSectionId[]>(load);

  const isShown = useCallback((id: CockpitSectionId) => !hidden.includes(id), [hidden]);

  const toggle = useCallback((id: CockpitSectionId) => {
    setHidden(current => {
      const next = current.includes(id) ? current.filter(h => h !== id) : [...current, id];
      save(next);
      return next;
    });
  }, []);

  return { isShown, toggle, hiddenCount: hidden.length };
}
