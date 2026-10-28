import { ChevronsRight, Star, ThumbsUp, Timer, TrendingDown, TrendingUp } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { FlashColor } from '@/hooks/race-control/useLapFlash';
import { cn } from '@/lib/utils';

/** The states a timing row shows an icon for: the lap flashes, and a car running close to the one ahead and a car on pace for its predicted laps. */
export type StateKind = FlashColor | 'close' | 'on-pace';

interface StateInfo {
  icon: LucideIcon;
  /** What the icon says, for the screen reader and the colour key. */
  label: string;
  /** The text colour of the icon, in the hue of the row tint it goes with. */
  tone: string;
}

/**
 * One icon per colour state. The icons differ in outline, not only in colour, so a row can be read by someone
 * who cannot tell purple, blue, green and red apart. The row tints stay as they are: the icon backs the colour
 * up, it does not replace it. The colour key ({@code TimingLegend}) lists the same icons.
 */
export const STATE_INFO: Record<StateKind, StateInfo> = {
  'race-best': { icon: Star, label: 'Fastest lap of the race', tone: 'text-purple-500' },
  'personal-best': { icon: Timer, label: 'Driver’s best lap', tone: 'text-blue-500' },
  improving: { icon: TrendingUp, label: 'As quick as, or quicker than, their last lap', tone: 'text-green-500' },
  slow: { icon: TrendingDown, label: 'Slower than their last lap or their average', tone: 'text-red-500' },
  close: { icon: ChevronsRight, label: 'Close to the car ahead', tone: 'text-amber-500' },
  'on-pace': { icon: ThumbsUp, label: 'On pace for its predicted laps', tone: 'text-green-500' },
};

/**
 * The icon for a row's current state, sized to the text around it. The filled star for the race's fastest lap
 * is the one icon with a solid shape, so it stands out from the outlined ones at a distance.
 */
export function StateIcon({ kind, className, decorative = false }: { kind: StateKind; className?: string; decorative?: boolean }) {
  const { icon: Icon, label, tone } = STATE_INFO[kind];
  return (
    <Icon
      {...(decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': label })}
      data-state={kind}
      className={cn('inline-block h-[1em] w-[1em] shrink-0 align-[-0.125em]', tone, className)}
      fill={kind === 'race-best' ? 'currentColor' : 'none'}
      strokeWidth={2.5}
    />
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
