import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  calculateAllFinals,
  calculateFinals,
  getFinalsLineup,
  moveFinalsDriver,
  swapFinalsDrivers,
  type FinalsLineupDto,
} from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getApiErrorMessage } from '@/lib/errors';

type Props = {
  eventId: number;
  eventClassId: number;
  canEdit: boolean;
};

/**
 * The class's finals line-up, between qualifying and the finals: a Calculate finals button (with the cars per final,
 * which starts at the size of a qualifying heat), then who is on each final's grid with ways to move a driver to
 * another final or swap two. Nothing is calculated by itself when qualifying ends. Editing is open until a final is
 * called to the grid; calculating again throws the edits away, so it asks first once the finals have drivers.
 */
export function FinalsLineupPanel({ eventId, eventClassId, canEdit }: Props) {
  const queryClient = useQueryClient();
  const [cars, setCars] = useState<string | null>(null);
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.finalsLineup(eventId),
    queryFn: () => getFinalsLineup(eventId),
  });
  const lineup: FinalsLineupDto | undefined = data?.find((l) => l.eventClassId === eventClassId);

  const refresh = () => queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all });
  const onError = (fallback: string) => (err: unknown) => toast.error(getApiErrorMessage(err, fallback));

  const calculate = useMutation({
    mutationFn: (carsPerFinal: number | null) => calculateFinals(eventId, eventClassId, carsPerFinal),
    onSuccess: () => {
      toast.success('Finals calculated');
      refresh();
    },
    onError: onError('Could not calculate the finals'),
  });
  const calculateAll = useMutation({
    mutationFn: () => calculateAllFinals(eventId),
    onSuccess: (result) => {
      const left = Object.values(result.skipped);
      const message = `Finals calculated for ${result.calculated.length} ${result.calculated.length === 1 ? 'class' : 'classes'}`
        + (left.length > 0 ? `; ${left.length} left alone (${[...new Set(left)].join(', ').toLowerCase()})` : '');
      if (result.calculated.length === 0) toast.info(message);
      else toast.success(message);
      refresh();
    },
    onError: onError('Could not calculate the finals'),
  });
  const move = useMutation({
    mutationFn: (v: { raceId: number; entryId: number; targetRaceId: number }) =>
      moveFinalsDriver(v.raceId, { entryId: v.entryId, targetRaceId: v.targetRaceId, targetPosition: null }),
    onSuccess: refresh,
    onError: onError('The driver could not be moved'),
  });
  const swap = useMutation({
    mutationFn: (v: { raceId: number; entryId: number; otherRaceId: number; otherEntryId: number }) =>
      swapFinalsDrivers(v.raceId, { entryId: v.entryId, otherRaceId: v.otherRaceId, otherEntryId: v.otherEntryId }),
    onSuccess: refresh,
    onError: onError('The drivers could not be swapped'),
  });

  if (isPending) return <p role="status" className="text-sm text-muted-foreground">Loading…</p>;
  if (isError) {
    return (
      <div role="alert" className="text-sm text-destructive">
        Could not load the finals line-up.{' '}
        <button type="button" className="underline" onClick={() => refetch()}>Retry</button>
      </div>
    );
  }
  if (!lineup) return null;

  const carsValue = cars ?? String(lineup.defaultCarsPerFinal || '');
  const carsNumber = carsValue === '' ? null : Number(carsValue);
  const canCalculate = canEdit && lineup.qualifyingComplete && !lineup.started && !calculate.isPending
    && (carsNumber === null || (Number.isInteger(carsNumber) && carsNumber >= 1));

  const readyClasses = (data ?? []).filter((l) => l.qualifyingComplete && !l.started && l.finals.length > 0);
  const anyCalculated = (data ?? []).some((l) => l.calculated);

  function onCalculateAll() {
    if (anyCalculated
      && !window.confirm('Calculate the finals of every class again? Any changes you made to the line-ups will be lost.')) {
      return;
    }
    calculateAll.mutate();
  }

  function onCalculate() {
    if (lineup?.calculated
      && !window.confirm('Calculate the finals again? Any changes you made to the line-ups will be lost.')) {
      return;
    }
    calculate.mutate(carsNumber);
  }

  return (
    <Card className="p-4 space-y-3" aria-label="Finals line-up">
      <h2 className="text-sm font-semibold">Finals line-up: {lineup.className}</h2>
      {canEdit && (
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm">
            Cars per final
            <input
              type="number"
              min={1}
              className="w-16 rounded border bg-background px-2 py-1 text-sm"
              value={carsValue}
              disabled={lineup.started}
              onChange={(ev) => setCars(ev.target.value)}
            />
          </label>
          <Button variant="outline" onClick={onCalculate} disabled={!canCalculate}>
            {calculate.isPending ? 'Calculating…' : lineup.calculated ? 'Recalculate finals' : 'Calculate finals'}
          </Button>
          <Button
            variant="outline"
            onClick={onCalculateAll}
            disabled={readyClasses.length === 0 || calculateAll.isPending}
          >
            {calculateAll.isPending ? 'Calculating…' : 'Calculate finals for all classes'}
          </Button>
          {!lineup.qualifyingComplete && !lineup.started && (
            <span className="text-sm text-muted-foreground">Finish or abandon every qualifying heat first.</span>
          )}
          {lineup.started && (
            <span className="text-sm text-muted-foreground">A final has been called, so the line-ups are locked.</span>
          )}
        </div>
      )}
      {!lineup.calculated ? (
        <p className="text-sm text-muted-foreground">The finals have not been calculated yet.</p>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {lineup.finals.map((fin) => (
            <section key={fin.raceId} aria-label={`${fin.letter} final`} className="rounded border p-2 space-y-1">
              <h3 className="text-sm font-medium">
                {fin.letter} final
                <span className="ml-2 text-xs text-muted-foreground">
                  {fin.drivers.length} {fin.drivers.length === 1 ? 'driver' : 'drivers'}
                  {fin.bumpSlots > 0 && ` + ${fin.bumpSlots} to bump up`}
                </span>
              </h3>
              <ol className="space-y-1">
                {fin.drivers.map((d, i) => (
                  <li key={d.entryId} className="flex items-center justify-between gap-2 text-sm">
                    <span>
                      <span className="tabular-nums text-muted-foreground mr-2">{d.gridPosition ?? '–'}</span>
                      {d.driverName}
                      {d.qualifyingPosition != null && (
                        <span className="ml-1 text-xs text-muted-foreground">(Q{d.qualifyingPosition})</span>
                      )}
                    </span>
                    {canEdit && fin.editable && fin.letter !== 'Z' && (
                      <span className="flex gap-1">
                        <select
                          aria-label={`Move ${d.driverName} to`}
                          className="rounded border bg-background px-1 py-0.5 text-xs"
                          value=""
                          disabled={move.isPending}
                          onChange={(ev) => {
                            if (ev.target.value) {
                              move.mutate({ raceId: fin.raceId, entryId: d.entryId, targetRaceId: Number(ev.target.value) });
                            }
                          }}
                        >
                          <option value="">Move to…</option>
                          {lineup.finals.filter((o) => o.raceId !== fin.raceId && o.editable && o.letter !== 'Z').map((o) => (
                            <option key={o.raceId} value={o.raceId}>{o.letter} final</option>
                          ))}
                        </select>
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-6 px-1.5 text-xs"
                          aria-label={`Move ${d.driverName} up`}
                          disabled={i === 0 || swap.isPending}
                          onClick={() => swap.mutate({
                            raceId: fin.raceId, entryId: d.entryId,
                            otherRaceId: fin.raceId, otherEntryId: fin.drivers[i - 1].entryId,
                          })}
                        >
                          ↑
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-6 px-1.5 text-xs"
                          aria-label={`Move ${d.driverName} down`}
                          disabled={i === fin.drivers.length - 1 || swap.isPending}
                          onClick={() => swap.mutate({
                            raceId: fin.raceId, entryId: d.entryId,
                            otherRaceId: fin.raceId, otherEntryId: fin.drivers[i + 1].entryId,
                          })}
                        >
                          ↓
                        </Button>
                      </span>
                    )}
                  </li>
                ))}
              </ol>
            </section>
          ))}
        </div>
      )}
    </Card>
  );
}
