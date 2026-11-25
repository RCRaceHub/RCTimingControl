// After a walk-in is saved, race control can put them in a heat. Only heats that have not been called take a new
// driver, so this lists those of the driver's class; rounds that have already run are skipped.
import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { QueryError } from '@/components/QueryError';
import { useAddEntryToHeat } from '@/hooks/race-control/useAddEntryToHeat';
import { useRunOrder } from '@/hooks/race-control/useRunOrder';
import { getApiErrorMessage } from '@/lib/errors';
import { roundName } from '@/lib/format';

interface Props {
  eventId: number;
  eventClassId: number;
  entryId: number;
  onDone: () => void;
}

export function AddToHeatCard({ eventId, eventClassId, entryId, onDone }: Props) {
  const { data: runOrder, isLoading, isError, refetch } = useRunOrder(eventId);
  const add = useAddEntryToHeat();
  const [allRounds, setAllRounds] = useState(true);
  const [chosen, setChosen] = useState<number | null>(null);

  if (isLoading) return <p className="text-sm text-muted-foreground">Looking for heats…</p>;
  if (isError) return <QueryError message="The run order could not be loaded." onRetry={() => void refetch()} />;

  const all = (runOrder ?? []).filter((r) => r.eventClassId === eventClassId && r.finalLetter === null
    && r.roundType !== 'FINAL');
  const pending = all.filter((r) => r.status === 'PENDING')
    .sort((a, b) => a.sequenceInEvent - b.sequenceInEvent);
  const started = all.some((r) => r.status !== 'PENDING');

  if (all.length === 0) {
    return (
      <Card className="space-y-2 p-4 text-sm">
        <p>This class has no heats yet, so there is nothing to add them to. Generate the rounds and they will be in.</p>
        <Button size="sm" variant="outline" onClick={onDone}>Done</Button>
      </Card>
    );
  }
  if (pending.length === 0) {
    return (
      <Card className="space-y-2 p-4 text-sm">
        <p>Every heat of this class has been called or run, so the driver can't be placed in one.</p>
        <Button size="sm" variant="outline" onClick={onDone}>Done</Button>
      </Card>
    );
  }

  const selected = chosen ?? pending[0].raceId;
  return (
    <Card className="space-y-3 p-4">
      <h2 className="font-semibold">Put them in a heat</h2>
      <p className="text-sm text-muted-foreground">
        The run order is already set, so a new driver is not in any heat yet. They go at the back of the heat you pick
        {started ? '; heats that have already been called or run are not offered' : ''}.
      </p>
      <label className="flex flex-col gap-1 text-sm">
        Heat
        <select
          className="rounded border bg-background px-2 py-1"
          value={selected}
          onChange={(ev) => setChosen(Number(ev.target.value))}
        >
          {pending.map((h) => (
            <option key={h.raceId} value={h.raceId}>
              {roundName(h, true)} heat {h.heatNumber}
            </option>
          ))}
        </select>
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={allRounds} onChange={(ev) => setAllRounds(ev.target.checked)} />
        Also the same heat in every later round that has not run
      </label>
      <div className="flex gap-2">
        <Button
          disabled={add.isPending}
          onClick={() =>
            add.mutate(
              { raceId: selected, entryId, allRounds },
              {
                onSuccess: () => {
                  toast.success('Driver added to the heat');
                  onDone();
                },
                onError: (err) => toast.error(getApiErrorMessage(err, 'The driver could not be added to the heat')),
              },
            )
          }
        >
          {add.isPending ? 'Adding…' : 'Add to heat'}
        </Button>
        <Button variant="outline" onClick={onDone}>Not now</Button>
      </div>
    </Card>
  );
}
