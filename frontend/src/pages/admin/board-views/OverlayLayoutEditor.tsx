import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  COMPONENT_LABELS, OVERLAY_COMPONENTS, OVERLAY_ZONES, ZONE_LABELS, defaultPlacement, partsInZone, samePlacement,
  type OverlayComponent, type OverlayPlacement, type OverlayZone,
} from '@/pages/boards/overlayLayout';

const DRAG_TYPE = 'application/x-overlay-part';

/** Where each zone sits on the sketch of the screen. */
const ZONE_CLASS: Record<OverlayZone, string> = {
  'top-strip': 'col-span-3',
  'top-left': 'row-start-2 col-start-1',
  'top-center': 'row-start-2 col-start-2',
  'top-right': 'row-start-2 col-start-3',
  'bottom-left': 'row-start-3 col-start-1',
  'bottom-center': 'row-start-3 col-start-2',
  'bottom-right': 'row-start-3 col-start-3',
  'bottom-strip': 'col-span-3',
};

/**
 * Where the parts of the streaming overlay sit (#667): a sketch of the screen cut into the overlay's zones, with a
 * chip for each part that is dragged from one zone to another. Each part also has a drop-down of zones, for the
 * keyboard and for touch screens. Parts that are switched off stay on the sketch, dimmed, so their place is kept for
 * when they are switched back on.
 *
 * @param placement the zone each part is in now
 * @param off the parts the design has switched off
 * @param onChange called with the new placement after a move or a reset
 */
export function OverlayLayoutEditor({
  placement,
  off,
  onChange,
}: {
  placement: OverlayPlacement;
  off: ReadonlySet<OverlayComponent>;
  onChange: (placement: OverlayPlacement) => void;
}) {
  const [over, setOver] = useState<OverlayZone | null>(null);
  const move = (part: OverlayComponent, zone: OverlayZone) => {
    if (placement[part] !== zone) onChange({ ...placement, [part]: zone });
  };

  return (
    <div className="space-y-3">
      <div
        className="grid grid-cols-3 grid-rows-[auto_1fr_1fr_auto] gap-1.5 rounded-md border bg-muted/40 p-1.5"
        data-testid="layout-canvas"
      >
        {OVERLAY_ZONES.map((zone) => (
          <div
            key={zone}
            data-testid={`zone-${zone}`}
            aria-label={ZONE_LABELS[zone]}
            className={cn(
              'flex min-h-14 flex-col gap-1 rounded border border-dashed p-1',
              ZONE_CLASS[zone],
              over === zone && 'border-primary bg-primary/10',
            )}
            onDragOver={(e) => {
              e.preventDefault();
              setOver(zone);
            }}
            onDragLeave={() => setOver((z) => (z === zone ? null : z))}
            onDrop={(e) => {
              e.preventDefault();
              setOver(null);
              const part = e.dataTransfer.getData(DRAG_TYPE);
              if ((OVERLAY_COMPONENTS as readonly string[]).includes(part)) move(part as OverlayComponent, zone);
            }}
          >
            <span className="text-[10px] uppercase tracking-wide text-muted-foreground">{ZONE_LABELS[zone]}</span>
            {partsInZone(placement, zone).map((part) => (
              <span
                key={part}
                draggable
                data-testid={`chip-${part}`}
                className={cn(
                  'cursor-grab select-none rounded bg-primary px-2 py-1 text-xs text-primary-foreground',
                  off.has(part) && 'opacity-40',
                )}
                onDragStart={(e) => {
                  e.dataTransfer.setData(DRAG_TYPE, part);
                  e.dataTransfer.effectAllowed = 'move';
                }}
              >
                {COMPONENT_LABELS[part]}
                {off.has(part) && ' (off)'}
              </span>
            ))}
          </div>
        ))}
      </div>
      <div className="space-y-1.5">
        {OVERLAY_COMPONENTS.map((part) => (
          <div key={part} className="flex items-center justify-between gap-2 text-sm">
            {COMPONENT_LABELS[part]}
            <select
              aria-label={`${COMPONENT_LABELS[part]} zone`}
              className="h-8 rounded border bg-background px-2"
              value={placement[part]}
              onChange={(e) => move(part, e.target.value as OverlayZone)}
            >
              {OVERLAY_ZONES.map((zone) => (
                <option key={zone} value={zone}>{ZONE_LABELS[zone]}</option>
              ))}
            </select>
          </div>
        ))}
      </div>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={samePlacement(placement, defaultPlacement())}
        onClick={() => onChange(defaultPlacement())}
      >
        Reset layout
      </Button>
    </div>
  );
}
