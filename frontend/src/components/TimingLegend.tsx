import { cn } from '@/lib/utils';

/** The groups of colours the timing views use; a view lists the ones it shows. */
export type LegendKind = 'lap' | 'close' | 'movement';

interface LegendItem {
  /** A solid Tailwind background that matches the row tint it explains. */
  swatch: string;
  label: string;
}

/**
 * What each colour means, in one place so every view says the same thing. The lap colours are the ones
 * {@code useLapFlash} gives a row for a moment after a lap; close is the tint for a car running near the one
 * ahead; movement is the places gained or lost in the qualifying tables.
 */
const ITEMS: Record<LegendKind, LegendItem[]> = {
  lap: [
    { swatch: 'bg-purple-500', label: 'Fastest lap of the race' },
    { swatch: 'bg-blue-500', label: 'Driver’s best lap' },
    { swatch: 'bg-green-500', label: 'Faster than their last lap' },
    { swatch: 'bg-red-500', label: 'Slower than usual' },
  ],
  close: [{ swatch: 'bg-amber-500', label: 'Close to the car ahead' }],
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
          <span aria-hidden="true" className={cn('inline-block rounded-sm', board ? 'h-4 w-4' : 'h-2.5 w-2.5', item.swatch)} />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
