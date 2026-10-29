import { createContext, useContext, useMemo } from 'react';
import type { ReactNode } from 'react';
import { ROW_ICONS } from '@/lib/rowIcons';
import type { RowIcon } from '@/lib/rowIcons';

const ALL_ICONS: ReadonlySet<RowIcon> = new Set(ROW_ICONS);
const RowIconsContext = createContext<ReadonlySet<RowIcon>>(ALL_ICONS);

/**
 * Says which row icons the screen below it shows (#696): the club's setting for race control and the boards, the
 * theme's or the address's for the streaming overlay. Without one every icon shows.
 */
export function RowIconsProvider({ shown, children }: { shown: readonly RowIcon[]; children: ReactNode }) {
  // Keyed on the names, so a caller passing a fresh array each render does not re-render every icon
  const key = shown.join(',');
  const set = useMemo(() => new Set(key === '' ? [] : (key.split(',') as RowIcon[])), [key]);
  return <RowIconsContext.Provider value={set}>{children}</RowIconsContext.Provider>;
}

/** Whether this screen shows the given icon beside a driver's name. */
export function useRowIcon(icon: RowIcon): boolean {
  return useContext(RowIconsContext).has(icon);
}
