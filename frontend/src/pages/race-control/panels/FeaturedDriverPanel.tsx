import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { useFeatureDriver } from '@/hooks/race-control/useFeatureDriver';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';

/**
 * Lets the race director pop a lower third for one driver on the streaming overlay (#533): pick a car, press Show.
 * The overlay hides it again by itself after a few seconds. It has no effect on a stream whose overlay was opened
 * with the lower third switched off.
 */
export function FeaturedDriverPanel({ raceId }: { raceId: number }) {
  const { rows } = useLiveTiming(raceId);
  const feature = useFeatureDriver(raceId);
  const [entryId, setEntryId] = useState<number | ''>('');
  if (rows.length === 0) return null;
  return (
    <section aria-label="Stream lower third" className="mt-2 flex flex-wrap items-center gap-2 text-sm">
      <label className="flex items-center gap-2 text-muted-foreground">
        Stream lower third
        <select
          className="h-8 rounded border bg-background px-2 text-foreground"
          value={entryId}
          onChange={e => setEntryId(e.target.value === '' ? '' : Number(e.target.value))}
          aria-label="Driver for the stream lower third"
        >
          <option value="">Choose a driver</option>
          {rows.map(row => (
            <option key={row.entryId} value={row.entryId}>
              {row.position}. {row.carNumber != null ? `#${row.carNumber} ` : ''}{row.driverName}
            </option>
          ))}
        </select>
      </label>
      <Button
        variant="outline"
        size="sm"
        disabled={entryId === '' || feature.isPending}
        onClick={() => entryId !== '' && feature.mutate(entryId)}
      >
        Show on stream
      </Button>
    </section>
  );
}
