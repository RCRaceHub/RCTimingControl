import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { partsInZone, type OverlayComponent, type OverlayPlacement, type OverlayZone } from './overlayLayout';

/** The strips of text and logos that span their zone, wherever they are dropped. */
const FULL_WIDTH = new Set<OverlayComponent>(['ticker', 'sponsors']);

/** A zone's parts, stacked from the screen edge inwards; a zone with nothing to show renders nothing. */
function Zone({
  zone,
  placement,
  blocks,
  className,
}: {
  zone: OverlayZone;
  placement: OverlayPlacement;
  blocks: Partial<Record<OverlayComponent, ReactNode>>;
  className?: string;
}) {
  const parts = partsInZone(placement, zone).filter((part) => blocks[part]);
  return (
    <div
      data-zone={zone}
      className={cn('flex min-w-0 gap-4', zone.startsWith('bottom') ? 'flex-col-reverse' : 'flex-col', className)}
    >
      {parts.map((part) => (
        <div key={part} data-part={part} className={cn('empty:hidden has-[>:empty]:hidden', FULL_WIDTH.has(part) && 'w-full')}>
          {blocks[part]}
        </div>
      ))}
    </div>
  );
}

/**
 * Lays the overlay's parts out in the zones an official chose (#667): a strip across the top, a strip across the
 * foot, and between them a row of three cells at the top and another at the foot. Parts sharing a zone stack, with
 * the sponsor bar and ticker nearest the edge, so nothing needs a hand-worked offset to clear its neighbours. The
 * page is transparent apart from the parts themselves, and only the parts take up space.
 *
 * @param placement the zone each part sits in
 * @param blocks what to draw for each part; a part left out (or null) is not shown
 */
export function OverlayZones({
  placement,
  blocks,
}: {
  placement: OverlayPlacement;
  blocks: Partial<Record<OverlayComponent, ReactNode>>;
}) {
  const zone = (name: OverlayZone, className?: string) => (
    <Zone zone={name} placement={placement} blocks={blocks} className={className} />
  );
  return (
    <div className="fixed inset-0 flex flex-col font-sans">
      {zone('top-strip')}
      <div className="flex min-h-0 flex-1 flex-col justify-between gap-4 p-4">
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-start gap-4">
          {zone('top-left', 'items-start')}
          {zone('top-center', 'items-center')}
          {zone('top-right', 'items-end')}
        </div>
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-end gap-4">
          {zone('bottom-left', 'items-start')}
          {zone('bottom-center', 'items-center')}
          {zone('bottom-right', 'items-end')}
        </div>
      </div>
      {zone('bottom-strip')}
    </div>
  );
}
