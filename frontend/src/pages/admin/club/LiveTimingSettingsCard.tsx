import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useLiveTimingSettings, useSetLiveTimingSettings } from '@/hooks/admin/useAdminClub';
import type { LiveTimingSettingsDto } from '@/lib/adminApi';

/** One threshold: how it is labelled, stored (milliseconds, or laps) and shown (seconds, or laps), and its range. */
interface Field {
  key: keyof LiveTimingSettingsDto;
  label: string;
  help: string;
  /** Milliseconds per shown unit; 1 for laps. */
  unit: number;
  max: number;
  step: number;
}

const FIELDS: Field[] = [
  {
    key: 'battleGapMs',
    label: 'Battle gap (seconds)',
    help: 'Two cars on the same lap this close together count as a battle. 0 switches battles off.',
    unit: 1000, max: 10, step: 0.1,
  },
  {
    key: 'battleTrendMsPerLap',
    label: 'Closing or growing (seconds a lap)',
    help: 'A gap changing by more than this each lap is shown as closing or growing, otherwise as holding.',
    unit: 1000, max: 2, step: 0.05,
  },
  {
    key: 'battleFastMsPerLap',
    label: 'Gaining fast (seconds a lap)',
    help: 'A gap closing by this much or more each lap marks the car behind as gaining fast. Not below the figure above.',
    unit: 1000, max: 5, step: 0.05,
  },
  {
    key: 'battleHoldLaps',
    label: 'Battle hold (laps)',
    help: 'How many laps a pair stays a battle after it drops outside the battle gap, so it does not flicker.',
    unit: 1, max: 10, step: 1,
  },
  {
    key: 'closeGapMs',
    label: 'Close car tint (seconds)',
    help: 'A car this near the one ahead gets an amber row on the timing screens. 0 switches the tint off.',
    unit: 1000, max: 60, step: 0.1,
  },
  {
    key: 'lateMarginMs',
    label: 'Late lap bar (seconds)',
    help: 'How far past its average lap a car is before its lap bar turns red.',
    unit: 1000, max: 10, step: 0.1,
  },
];

/** The value a field shows for a saved setting. */
function shown(field: Field, settings: LiveTimingSettingsDto): string {
  return String(settings[field.key] / field.unit);
}

/**
 * The club's live timing thresholds (#505): when two cars are in a battle and which way it is going, how near counts as
 * close, and when a lap bar turns red. Races in progress take a change at their next update. The server checks the
 * ranges again.
 */
export default function LiveTimingSettingsCard() {
  const { data, isLoading, isError } = useLiveTimingSettings();
  const save = useSetLiveTimingSettings();
  // What the admin has typed, by field; empty while they have not changed the saved values
  const [draft, setDraft] = useState<Partial<Record<keyof LiveTimingSettingsDto, string>>>({});

  if (isLoading) return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  if (isError || !data) {
    return <p className="text-sm text-destructive">Could not load the live timing settings.</p>;
  }

  const values = FIELDS.map((field) => {
    const text = draft[field.key] ?? shown(field, data);
    const number = Number(text);
    const valid = text.trim() !== '' && Number.isFinite(number) && number >= 0 && number <= field.max;
    return { field, text, number, valid };
  });
  const trend = values.find((v) => v.field.key === 'battleTrendMsPerLap')!;
  const fast = values.find((v) => v.field.key === 'battleFastMsPerLap')!;
  const fastBelowTrend = trend.valid && fast.valid && fast.number < trend.number;
  const allValid = values.every((v) => v.valid) && !fastBelowTrend;
  const changed = Object.keys(draft).length > 0;

  async function onSave() {
    const settings = { ...data } as LiveTimingSettingsDto;
    for (const { field, number } of values) settings[field.key] = Math.round(number * field.unit);
    try {
      await save.mutateAsync(settings);
      setDraft({});
      toast.success('Live timing settings saved');
    } catch {
      toast.error('Could not save the live timing settings. Try again.');
    }
  }

  return (
    <section className="rounded-lg border p-4 space-y-4" aria-labelledby="live-timing-settings-heading">
      <div>
        <h2 id="live-timing-settings-heading" className="text-sm font-semibold">Live timing thresholds</h2>
        <p className="text-xs text-muted-foreground">
          These apply to the referee screen, the live race board, the board views and the streaming overlay. A change
          reaches a race in progress at its next update. An address option such as close=2 on a board still wins over
          the close car tint here.
        </p>
      </div>
      {values.map(({ field, text, valid }) => (
        <div key={field.key} className="space-y-1.5">
          <Label htmlFor={`live-timing-${field.key}`}>{field.label}</Label>
          <Input
            id={`live-timing-${field.key}`}
            type="number"
            inputMode="decimal"
            min={0}
            max={field.max}
            step={field.step}
            value={text}
            onChange={(e) => setDraft((d) => ({ ...d, [field.key]: e.target.value }))}
            className="w-32"
          />
          <p className="text-xs text-muted-foreground">{field.help}</p>
          {!valid && <p className="text-xs text-destructive">Enter a number from 0 to {field.max}.</p>}
        </div>
      ))}
      {fastBelowTrend && (
        <p className="text-xs text-destructive">The gaining-fast figure cannot be below the closing or growing figure.</p>
      )}
      <Button onClick={onSave} disabled={!changed || !allValid || save.isPending}>
        {save.isPending ? 'Saving…' : 'Save live timing settings'}
      </Button>
    </section>
  );
}
