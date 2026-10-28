import { useEffect, useState } from 'react';
import { isEntryDrag, readEntryDrag, startEntryDrag } from '@/lib/dragEntry';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { useConfirm } from '@/components/ConfirmDialog';
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
  /** Leaves out the all-classes button, for the line-up screen that has its own. */
  hideCalculateAll?: boolean;
  /** Told the cars per final shown for the class (null when blank), so a button elsewhere can calculate with it. */
  onCarsChange?: (eventClassId: number, cars: number | null) => void;
};

/**
 * The class's finals line-up, between qualifying and the finals: a Calculate finals button (with the cars per final,
 * which starts at the size of a qualifying heat), then who is on each final's grid with ways to move a driver to
 * another final or swap two. Nothing is calculated by itself when qualifying ends. Editing is open until a final is
 * called to the grid; calculating again throws the edits away, so it asks first once the finals have drivers.
 */
export function FinalsLineupPanel({ eventId, eventClassId, canEdit, hideCalculateAll = false, onCarsChange }: Props) {
  const queryClient = useQueryClient();
  const { confirm, dialog } = useConfirm();
  const [cars, setCars] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<number | null>(null);
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.finalsLineup(eventId),
    queryFn: () => getFinalsLineup(eventId),
  });
  const lineup: FinalsLineupDto | undefined = data?.find((l) => l.eventClassId === eventClassId);

  const carsValue = cars ?? String(lineup?.defaultCarsPerFinal || '');
  const carsNumber = carsValue === '' ? null : Number(carsValue);
  useEffect(() => {
    if (lineup) onCarsChange?.(eventClassId, carsNumber);
  }, [lineup, eventClassId, carsNumber, onCarsChange]);

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
    mutationFn: () => calculateAllFinals(eventId, carsNumber !== null && Number.isInteger(carsNumber) && carsNumber >= 1
      ? { [eventClassId]: carsNumber } : {}),
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

  const canCalculate = canEdit && lineup.qualifyingComplete && !lineup.started && !calculate.isPending
    && (carsNumber === null || (Number.isInteger(carsNumber) && carsNumber >= 1));

  const readyClasses = (data ?? []).filter((l) => l.qualifyingComplete && !l.started && l.finals.length > 0);
  const anyCalculated = (data ?? []).some((l) => l.calculated);

  async function onCalculateAll() {
    if (anyCalculated && !(await confirm({
      title: 'Calculate the finals of every class again?',
      description: 'Any changes you made to the line-ups will be lost.',
      confirmLabel: 'Calculate again',
      destructive: true,
    }))) {
      return;
    }
    calculateAll.mutate();
  }

  async function onCalculate() {
    if (lineup?.calculated && !(await confirm({
      title: 'Calculate the finals again?',
      description: 'Any changes you made to the line-ups will be lost.',
      confirmLabel: 'Calculate again',
      destructive: true,
    }))) {
      return;
    }
    calculate.mutate(carsNumber);
  }

  return (
    <Card className="p-4 space-y-3" aria-label="Finals line-up">
      {dialog}
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
          {!hideCalculateAll && (
            <Button
              variant="outline"
              onClick={onCalculateAll}
              disabled={readyClasses.length === 0 || calculateAll.isPending}
            >
              {calculateAll.isPending ? 'Calculating…' : 'Calculate finals for all classes'}
            </Button>
          )}
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
          {lineup.finals.map((fin) => {
            const editable = canEdit && fin.editable && fin.letter !== 'Z';
            // A drag may come from another tab or a stale page: the source must be an editable final listed here
            const ownsDrag = (d: { raceId: number; entryId: number }) => lineup.finals.some((f) => f.raceId === d.raceId
              && canEdit && f.editable && f.letter !== 'Z' && f.drivers.some((x) => x.entryId === d.entryId));
            return (
            <section
              key={fin.raceId}
              aria-label={`${fin.letter} final`}
              className={`rounded border p-2 space-y-1 ${dropTarget === fin.raceId ? 'outline outline-2 outline-primary' : ''}`}
              onDragOver={editable ? (ev) => {
                if (!isEntryDrag(ev)) return;
                ev.preventDefault();
                setDropTarget(fin.raceId);
              } : undefined}
              onDragLeave={(ev) => {
                if (ev.currentTarget.contains(ev.relatedTarget as Node | null)) return;
                setDropTarget((t) => (t === fin.raceId ? null : t));
              }}
              onDrop={editable ? (ev) => {
                ev.preventDefault();
                setDropTarget(null);
                const dragged = readEntryDrag(ev);
                // Dropped on empty room in another final: the driver moves there. Dropped on a driver: see below.
                if (dragged && ownsDrag(dragged) && dragged.raceId !== fin.raceId && !move.isPending && !swap.isPending) {
                  move.mutate({ raceId: dragged.raceId, entryId: dragged.entryId, targetRaceId: fin.raceId });
                }
              } : undefined}
            >
              <h3 className="text-sm font-medium">
                {fin.letter} final
                <span className="ml-2 text-xs text-muted-foreground">
                  {fin.drivers.length} {fin.drivers.length === 1 ? 'driver' : 'drivers'}
                  {fin.bumpSlots > 0 && ` + ${fin.bumpSlots} to bump up`}
                </span>
              </h3>
              <ol className="space-y-1">
                {fin.drivers.map((d, i) => (
                  <li
                    key={d.entryId}
                    className={`flex items-center justify-between gap-2 text-sm ${editable ? 'cursor-grab' : ''}`}
                    draggable={editable}
                    onDragStart={(ev) => {
                      if (!editable) { ev.preventDefault(); return; }
                      startEntryDrag(ev, { raceId: fin.raceId, entryId: d.entryId });
                    }}
                    onDragEnd={() => setDropTarget(null)}
                    onDrop={editable ? (ev) => {
                      // Dropped on a driver: the two swap places, in this final or across finals
                      const dragged = readEntryDrag(ev);
                      if (!dragged || dragged.entryId === d.entryId || !ownsDrag(dragged) || move.isPending || swap.isPending) return;
                      ev.preventDefault();
                      ev.stopPropagation();
                      setDropTarget(null);
                      swap.mutate({
                        raceId: dragged.raceId, entryId: dragged.entryId,
                        otherRaceId: fin.raceId, otherEntryId: d.entryId,
                      });
                    } : undefined}
                  >
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
            );
          })}
        </div>
      )}
    </Card>
  );
}
