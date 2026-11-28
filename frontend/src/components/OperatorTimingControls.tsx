import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { TimingLegend } from '@/components/TimingLegend';
import type { LegendKind } from '@/components/TimingLegend';
import { ROW_ICON_LABELS } from '@/lib/rowIcons';
import type { RowIcon } from '@/lib/rowIcons';

interface Props {
  /** The icons the club allows; the ones it has hidden are not offered. */
  allowed: readonly RowIcon[];
  shown: readonly RowIcon[];
  onToggle: (icon: RowIcon) => void;
  onReset: () => void;
  customised: boolean;
  legendKinds: LegendKind[];
}

/**
 * Two small buttons for the head of a live timing table on race control and the referee screen (#766): **Icons**
 * picks what shows beside driver names on this device, and **Key** shows what the colours and icons mean on demand,
 * so the table is not shared with a permanent legend.
 */
export function OperatorTimingControls({ allowed, shown, onToggle, onReset, customised, legendKinds }: Props) {
  const [open, setOpen] = useState<'icons' | 'key' | null>(null);
  const toggleOpen = (which: 'icons' | 'key') => setOpen((cur) => (cur === which ? null : which));

  return (
    <div className="flex flex-col items-end gap-2">
      <div className="flex items-center" role="group" aria-label="Timing table display">
        <Button
          type="button"
          variant="outline"
          className="rounded-r-none pointer-coarse:min-h-11"
          aria-expanded={open === 'icons'}
          onClick={() => toggleOpen('icons')}
        >
          Icons
        </Button>
        <Button
          type="button"
          variant="outline"
          className="-ml-px rounded-l-none pointer-coarse:min-h-11"
          aria-expanded={open === 'key'}
          onClick={() => toggleOpen('key')}
        >
          Key
        </Button>
      </div>
      {open === 'icons' && (
        <div className="w-full rounded-md border bg-card p-3" role="group" aria-label="Icons beside driver names">
          <p className="mb-2 text-xs text-muted-foreground">Shown beside driver names on this device.</p>
          <div className="flex flex-col gap-2">
            {allowed.map((icon) => (
              <label key={icon} className="flex items-center gap-2 text-sm">
                <Checkbox checked={shown.includes(icon)} onCheckedChange={() => onToggle(icon)} />
                {ROW_ICON_LABELS[icon]}
              </label>
            ))}
          </div>
          {customised && (
            <Button type="button" variant="ghost" size="sm" className="mt-2" onClick={onReset}>
              Back to the usual icons
            </Button>
          )}
        </div>
      )}
      {open === 'key' && (
        <div className="rounded-md border bg-card p-3">
          <TimingLegend kinds={legendKinds} className="text-muted-foreground" />
        </div>
      )}
    </div>
  );
}
