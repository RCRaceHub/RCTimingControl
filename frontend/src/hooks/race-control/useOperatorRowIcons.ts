import { useState } from 'react';
import { OPERATOR_DEFAULT_ROW_ICONS, ROW_ICONS, rowIconsWithout } from '@/lib/rowIcons';
import type { RowIcon } from '@/lib/rowIcons';

const STORAGE_KEY = 'rc-operator-row-icons';

function readStored(): RowIcon[] | null {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    if (!Array.isArray(parsed)) return null;
    return ROW_ICONS.filter((icon) => parsed.includes(icon));
  } catch {
    return null;
  }
}

function writeStored(icons: RowIcon[] | null) {
  try {
    if (icons === null) localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, JSON.stringify(icons));
  } catch {
    // Private mode or blocked storage: the choice lasts until the page reloads
  }
}

/**
 * Which icons race control and the referee screen show beside driver names (#766). The device's own choice, kept in
 * local storage, wins over {@code OPERATOR_DEFAULT_ROW_ICONS}; an icon the club has hidden on the Club page
 * ({@code clubHidden}) is never shown, whatever the device says.
 */
export function useOperatorRowIcons(clubHidden: readonly string[] = []) {
  const [stored, setStored] = useState<RowIcon[] | null>(readStored);
  const allowed = rowIconsWithout(clubHidden);
  const chosen = stored ?? OPERATOR_DEFAULT_ROW_ICONS;
  const shown = allowed.filter((icon) => chosen.includes(icon));

  function toggle(icon: RowIcon) {
    const base = stored ?? [...OPERATOR_DEFAULT_ROW_ICONS];
    const next = ROW_ICONS.filter((i) => (i === icon ? !base.includes(i) : base.includes(i)));
    writeStored(next);
    setStored(next);
  }

  function reset() {
    writeStored(null);
    setStored(null);
  }

  return { shown, allowed, toggle, reset, customised: stored !== null };
}
