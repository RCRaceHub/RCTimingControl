import { ChevronsRight, CircleHelp, Flame, Gavel, Layers, Star, ThumbsUp, Timer, TrendingDown, TrendingUp } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { FlashColor } from '@/hooks/race-control/useLapFlash';
import { cn } from '@/lib/utils';

/**
 * The states a timing row shows an icon for: the lap flashes, a car running close to the one ahead, a car on pace
 * for its predicted laps, a car whose last lap was a flying lap, a lapped car, a car with a time penalty and a car the decoder has not seen for a while.
 */
export type StateKind = FlashColor | 'close' | 'on-pace' | 'flying' | 'lapped' | 'penalty' | 'no-signal';

interface StateInfo {
  icon: LucideIcon;
  /** What the icon says, for the screen reader and the colour key. */
  label: string;
  /** The text colour of the icon, in the hue of the row tint it goes with. */
  tone: string;
  /** The two-letter tag shown beside the icon in high-contrast mode, so the state reads without icon or colour. */
  tag: string;
}

/**
 * One icon per colour state. The icons differ in outline, not only in colour, so a row can be read by someone
 * who cannot tell purple, blue, green and red apart. The row tints stay as they are: the icon backs the colour
 * up, it does not replace it. The colour key ({@code TimingLegend}) lists the same icons.
 */
export const STATE_INFO: Record<StateKind, StateInfo> = {
  'race-best': { icon: Star, label: 'Fastest lap of the race', tone: 'text-purple-500', tag: 'FL' },
  'personal-best': { icon: Timer, label: 'Driver’s best lap', tone: 'text-info', tag: 'PB' },
  improving: { icon: TrendingUp, label: 'As quick as, or quicker than, their last lap', tone: 'text-success', tag: 'UP' },
  slow: { icon: TrendingDown, label: 'Slower than their last lap or their average', tone: 'text-destructive', tag: 'SL' },
  close: { icon: ChevronsRight, label: 'Close to the car ahead', tone: 'text-warning', tag: 'CL' },
  'on-pace': { icon: ThumbsUp, label: 'On pace for its predicted laps', tone: 'text-success', tag: 'ON' },
  flying: { icon: Flame, label: 'Flying lap: faster than its average so far', tone: 'text-warning', tag: 'FLY' },
  lapped: { icon: Layers, label: 'Laps down on the leader', tone: 'text-info', tag: 'LAP' },
  penalty: { icon: Gavel, label: 'Has a time penalty', tone: 'text-warning', tag: 'PEN' },
  'no-signal': { icon: CircleHelp, label: 'Not seen by the decoder for a while', tone: 'text-muted-foreground', tag: 'NS' },
};

/**
 * High contrast (#591, {@code contrast=high} on a board address) is a {@code data-contrast="high"} attribute that
 * {@link useHighContrast} puts on the page; these classes key off it. The icon gets a thick stroke and a dark outline
 * so it survives compressed stream video, and a short text tag appears beside it.
 */
// Whole class names, not built from parts, so Tailwind finds them when it scans the source
const HIGH_CONTRAST_ICON = [
  '[[data-contrast=high]_&]:stroke-[3.5]',
  '[[data-contrast=high]_&]:drop-shadow-[0_0_1px_rgba(0,0,0,0.95)]',
].join(' ');
const HIGH_CONTRAST_TAG = [
  '[[data-contrast=high]_&]:ml-0.5',
  '[[data-contrast=high]_&]:inline',
  '[[data-contrast=high]_&]:text-[0.8em]',
  '[[data-contrast=high]_&]:[text-shadow:0_0_2px_rgba(0,0,0,0.95)]',
].join(' ');

/**
 * The icon for a row's current state, sized to the text around it. The filled star for the race's fastest lap
 * is the one icon with a solid shape, so it stands out from the outlined ones at a distance.
 */
export function StateIcon({ kind, className, decorative = false }: { kind: StateKind; className?: string; decorative?: boolean }) {
  const { icon: Icon, label, tone, tag } = STATE_INFO[kind];
  return (
    <>
      <Icon
        {...(decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': label })}
        data-state={kind}
        className={cn(
          'inline-block h-[1em] w-[1em] shrink-0 align-[-0.125em]',
          HIGH_CONTRAST_ICON,
          tone,
          className,
        )}
        fill={kind === 'race-best' ? 'currentColor' : 'none'}
        strokeWidth={2.5}
      />
      {!decorative && (
        <span aria-hidden="true" data-tag={tag} className={cn('hidden font-bold tabular-nums', HIGH_CONTRAST_TAG, tone)}>
          {tag}
        </span>
      )}
    </>
  );
}

/**
 * The icon for a row: its lap flash while one is showing, otherwise the close icon when it is close, otherwise
 * nothing. A lap flash wins over close for the same reason it wins over the close tint.
 */
export function RowStateIcon({ flash, close, className }: { flash?: FlashColor; close: boolean; className?: string }) {
  const kind: StateKind | null = flash ?? (close ? 'close' : null);
  return kind ? <StateIcon kind={kind} className={className} /> : null;
}
