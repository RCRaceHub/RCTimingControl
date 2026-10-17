import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card } from '@/components/ui/card';
import { getRaceEntries, moveEntryToHeat, type RunOrderItemDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getApiErrorMessage } from '@/lib/errors';

type Props = {
  race: RunOrderItemDto;
  runOrder: RunOrderItemDto[];
};

/**
 * The drivers in a pending heat, each with a way to move them to another pending heat of the class in the same
 * round, such as to even up the heats after a late entry. Moving can also apply to the other rounds.
 */
export function HeatDriversPanel({ race, runOrder }: Props) {
  const queryClient = useQueryClient();
  const [allRounds, setAllRounds] = useState(true);

  const { data: entries = [] } = useQuery({
    queryKey: raceControlQueryKeys.raceEntries(race.raceId),
    queryFn: () => getRaceEntries(race.raceId),
  });

  const otherHeats = runOrder.filter((r) =>
    r.raceId !== race.raceId
    && r.eventClassId === race.eventClassId
    && r.roundType === race.roundType
    && r.roundNumber === race.roundNumber
    && r.finalLetter === null
    && r.status === 'PENDING');

  const move = useMutation({
    mutationFn: (v: { entryId: number; targetRaceId: number }) =>
      moveEntryToHeat(race.raceId, { ...v, allRounds }),
    onSuccess: () => {
      toast.success('Driver moved');
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all });
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'The driver could not be moved')),
  });

  if (race.finalLetter !== null || otherHeats.length === 0 || entries.length === 0) return null;

  return (
    <Card className="p-4 space-y-3">
      <h3 className="text-sm font-semibold">Drivers in this heat</h3>
      <ul className="space-y-2">
        {entries.map((e) => (
          <li key={e.entryId} className="flex items-center justify-between gap-2 text-sm">
            <span>
              {e.racerName}
              {e.carNumber && <span className="text-muted-foreground ml-1">• car #{e.carNumber}</span>}
            </span>
            <select
              aria-label={`Move ${e.racerName} to`}
              className="rounded border bg-background px-2 py-1 text-xs"
              value=""
              disabled={move.isPending}
              onChange={(ev) => {
                if (ev.target.value) move.mutate({ entryId: e.entryId, targetRaceId: Number(ev.target.value) });
              }}
            >
              <option value="">Move to…</option>
              {otherHeats.map((h) => (
                <option key={h.raceId} value={h.raceId}>Heat {h.heatNumber}</option>
              ))}
            </select>
          </li>
        ))}
      </ul>
      <label className="flex items-center gap-2 text-xs text-muted-foreground">
        <input type="checkbox" checked={allRounds} onChange={(ev) => setAllRounds(ev.target.checked)} />
        Also move them in the other rounds where both heats haven't been called
      </label>
    </Card>
  );
}
