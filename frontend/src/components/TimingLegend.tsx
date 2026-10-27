import { STATE_INFO, StateIcon } from '@/components/StateIcon';
import type { StateKind } from '@/components/StateIcon';
import { cn } from '@/lib/utils';

/** The groups of colours the timing views use; a view lists the ones it shows. */
export type LegendKind = 'lap' | 'close' | 'movement';

interface LegendItem {
  /** A solid Tailwind background in the hue of the row tint it explains; empty for a plain lead-in. */
  swatch: string;
  label: string;
  /** The row icon that goes with the colour, so the key shows both; none for the movement colours. */
  icon?: StateKind;
}

/** A key entry for a state that has an icon: the colour swatch, the icon and the words, all from {@code STATE_INFO}. */
const state = (swatch: string, icon: StateKind): LegendItem => ({ swatch, icon, label: STATE_INFO[icon].label });

/**
 * What each colour means, in one place so every view says the same thing. The lap colours are the ones
 * {@code useLapFlash} gives a row for a moment after a lap; close is the tint for a car running near the one
 * ahead; movement is the places gained or lost in the qualifying tables.
 */
const ITEMS: Record<LegendKind, LegendItem[]> = {
  lap: [
    { swatch: '', label: 'Flashes after each lap:' },
    state('bg-purple-500', 'race-best'),
    state('bg-blue-500', 'personal-best'),
    state('bg-green-500', 'improving'),
    state('bg-red-500', 'slow'),
  ],
  close: [state('bg-amber-500', 'close')],
  movement: [
    { swatch: 'bg-emerald-500', label: 'Moved up' },
    { swatch: 'bg-rose-500', label: 'Moved down' },
    { swatch: 'bg-sky-500', label: 'New to the table' },
    { swatch: 'bg-amber-500', label: 'Heat on track' },
  ],
};

interface TimingLegendProps {
  /** Which groups of colours to explain, in order. */
  kinds: LegendKind[];
  /** Larger type for a venue TV; the default suits a race control panel. */
  size?: 'small' | 'board';
  /** Extra classes, such as a text colour to match the page. */
  className?: string;
}

/**
 * A small key to the colours on a timing view, so people who are not sure what a purple or blue row means can
 * check. Shared by race control, the boards and the overlay, so the meanings stay the same everywhere.
 */
export function TimingLegend({ kinds, size = 'small', className }: TimingLegendProps) {
  const board = size === 'board';
  return (
    <ul
      aria-label="Colour key"
      data-testid="timing-legend"
      className={cn(
        'flex flex-wrap items-center gap-x-4 gap-y-1 opacity-80',
        board ? 'text-lg lg:text-xl' : 'text-xs',
        className,
      )}
    >
      {kinds.flatMap((kind) => ITEMS[kind]).map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          {item.swatch && (
            <span aria-hidden="true" className={cn('inline-block rounded-sm', board ? 'h-4 w-4' : 'h-2.5 w-2.5', item.swatch)} />
          )}
          {item.icon && <StateIcon kind={item.icon} decorative className={board ? 'h-5 w-5' : 'h-3.5 w-3.5'} />}
          {item.label}
        </li>
      ))}
    </ul>
  );
}
