import { useState } from 'react';
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card } from '@/components/ui/card';
import { getRaceEntries, moveEntryToHeat, type RunOrderItemDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { roundName } from '@/lib/format';
import { getApiErrorMessage } from '@/lib/errors';

type Props = {
  race: RunOrderItemDto;
  runOrder: RunOrderItemDto[];
  onSelect: (raceId: number) => void;
  canMove?: boolean;
};

/**
 * The one place to see and change who is in which heat of the selected practice or qualifying heat's round and
 * class, so the line-ups can be checked at the start of practice and before qualifying begins. The selected heat is
 * highlighted. A driver in a heat that has not been called can be moved to another such heat to balance them (the
 * heats need not stay the same size), either in this round only or in every round where both heats are still
 * waiting, which is how qualifying is evened up after practice. The heat names select that heat.
 */
export function HeatLineupsPanel({ race, runOrder, onSelect, canMove = false }: Props) {
  const queryClient = useQueryClient();
  const [allRounds, setAllRounds] = useState(true);
  const roundLabel = roundName(race, true);
  const move = useMutation({
    mutationFn: (v: { raceId: number; entryId: number; targetRaceId: number }) =>
      moveEntryToHeat(v.raceId, { entryId: v.entryId, targetRaceId: v.targetRaceId, allRounds }),
    onSuccess: () => {
      toast.success('Driver moved');
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all });
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'The driver could not be moved')),
  });

  const heats = runOrder
    .filter((r) => r.eventClassId === race.eventClassId && r.roundType === race.roundType
      && r.roundNumber === race.roundNumber && r.finalLetter === null)
    .sort((a, b) => a.heatNumber - b.heatNumber);
  const results = useQueries({
    queries: heats.map((h) => ({
      queryKey: raceControlQueryKeys.raceEntries(h.raceId),
      queryFn: () => getRaceEntries(h.raceId),
    })),
  });

  if (heats.length < 2) return null;

  return (
    <Card className="p-4 space-y-2" aria-label="Heat line-ups">
      <h3 className="text-sm font-semibold">
        {roundLabel} line-ups: {race.className}
      </h3>
      <div className="grid gap-3 md:grid-cols-2">
        {heats.map((h, i) => {
          const drivers = results[i]?.data ?? [];
          return (
            <section
              key={h.raceId}
              aria-label={`Heat ${h.heatNumber}`}
              aria-current={h.raceId === race.raceId ? 'true' : undefined}
              className={`rounded border p-2 space-y-1 ${h.raceId === race.raceId ? 'border-primary bg-primary/5 ring-1 ring-primary' : ''}`}
            >
              <button
                type="button"
                className="text-sm font-medium underline-offset-2 hover:underline"
                onClick={() => onSelect(h.raceId)}
              >
                Heat {h.heatNumber}
              </button>
              <span className="ml-2 text-xs text-muted-foreground">
                {drivers.length} {drivers.length === 1 ? 'driver' : 'drivers'}
              </span>
              <ol className="space-y-0.5 text-sm">
                {drivers.map((d) => (
                  <li key={d.entryId} className="flex items-center justify-between gap-2">
                    <span>
                      {d.racerName}
                      {d.carNumber && <span className="ml-1 text-muted-foreground">#{d.carNumber}</span>}
                    </span>
                    {canMove && h.status === 'PENDING' && (
                      <select
                        aria-label={`Move ${d.racerName} to`}
                        className="rounded border bg-background px-1 py-0.5 text-xs"
                        value=""
                        disabled={move.isPending}
                        onChange={(ev) => {
                          if (ev.target.value) {
                            move.mutate({ raceId: h.raceId, entryId: d.entryId, targetRaceId: Number(ev.target.value) });
                          }
                        }}
                      >
                        <option value="">Move to…</option>
                        {heats.filter((o) => o.raceId !== h.raceId && o.status === 'PENDING').map((o) => (
                          <option key={o.raceId} value={o.raceId}>Heat {o.heatNumber}</option>
                        ))}
                      </select>
                    )}
                  </li>
                ))}
              </ol>
            </section>
          );
        })}
      </div>
      {canMove && (
        <fieldset className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <legend className="sr-only">Where a move applies</legend>
          <span>Moves apply to:</span>
          <label className="flex items-center gap-1">
            <input type="radio" name="move-scope" checked={!allRounds} onChange={() => setAllRounds(false)} />
            Just {roundLabel}
          </label>
          <label className="flex items-center gap-1">
            <input type="radio" name="move-scope" checked={allRounds} onChange={() => setAllRounds(true)} />
            Every round where both heats are still waiting
          </label>
        </fieldset>
      )}
    </Card>
  );
}
