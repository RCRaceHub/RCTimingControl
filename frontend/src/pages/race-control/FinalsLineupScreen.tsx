import { useCallback, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import {
  calculateAllFinals,
  confirmFinalsLineup,
  getFinalsLineup,
} from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getApiErrorMessage } from '@/lib/errors';
import { useConfirm } from '@/components/ConfirmDialog';
import { FinalsLineupPanel } from './panels/FinalsLineupPanel';
import { QualifyingTablePanel } from './panels/QualifyingTablePanel';

type Props = {
  eventId: number;
};

/**
 * The race controller's screen once qualifying is over, shown in place of the cockpit until the finals line-ups are
 * confirmed: it only calculates the finals, lets every final's drivers be moved or swapped on one page (the same
 * panel the cockpit uses, one card for each class), and confirms. Confirming brings the cockpit back with Call Grid.
 * It is only shown to race directors and admins; calculating again after confirming brings it back.
 */
export function FinalsLineupScreen({ eventId }: Props) {
  const queryClient = useQueryClient();
  const { confirm, dialog } = useConfirm();
  const { data: lineups, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.finalsLineup(eventId),
    queryFn: () => getFinalsLineup(eventId),
  });
  // The cars per final shown on each class's card, so Calculate all uses them rather than the format's own size
  const carsByClass = useRef<Record<number, number>>({});
  const onCarsChange = useCallback((eventClassId: number, cars: number | null) => {
    if (cars !== null && Number.isInteger(cars) && cars >= 1) carsByClass.current[eventClassId] = cars;
    else delete carsByClass.current[eventClassId];
  }, []);
  const refresh = () => queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all });

  const calculateAll = useMutation({
    mutationFn: () => calculateAllFinals(eventId, { ...carsByClass.current }),
    onSuccess: (result) => {
      const left = Object.values(result.skipped);
      toast.success(`Finals calculated for ${result.calculated.length} ${result.calculated.length === 1 ? 'class' : 'classes'}`
        + (left.length > 0 ? `; ${left.length} left alone (${[...new Set(left)].join(', ').toLowerCase()})` : ''));
      refresh();
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'Could not calculate the finals')),
  });
  const confirmLineup = useMutation({
    mutationFn: () => confirmFinalsLineup(eventId),
    onSuccess: () => {
      toast.success('Finals line-up confirmed');
      refresh();
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'Could not confirm the line-up')),
  });

  const classes = lineups ?? [];
  const anyCalculated = classes.some((l) => l.calculated);
  // A class that finished qualifying but has no drivers on its finals yet is still to be calculated
  const toCalculate = classes.filter((l) => l.qualifyingComplete && !l.calculated);

  async function onCalculateAll() {
    if (anyCalculated) {
      const ok = await confirm({
        title: 'Calculate the finals of every class again?',
        description: 'Any changes you made to the line-ups will be lost.',
        confirmLabel: 'Calculate again',
        destructive: true,
      });
      if (!ok) return;
    }
    calculateAll.mutate();
  }

  // The race control layout does not scroll, so this screen scrolls itself
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-5xl flex-col gap-4 p-6" aria-label="Finals line-up">
        <header className="space-y-1">
          <h1 className="text-xl font-semibold">Qualifying is over: set the finals</h1>
          <p className="text-sm text-muted-foreground">
            Calculate the finals from the qualifying results, change who is in each final if you need to, then confirm
            the line-up to go back to race control and call the first final.
          </p>
        </header>

        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" onClick={onCalculateAll} disabled={calculateAll.isPending}>
            {calculateAll.isPending ? 'Calculating…' : anyCalculated ? 'Calculate all finals again' : 'Calculate finals for all classes'}
          </Button>
          <Button
            onClick={() => confirmLineup.mutate()}
            disabled={confirmLineup.isPending || isPending || toCalculate.length > 0 || !anyCalculated}
          >
            {confirmLineup.isPending ? 'Confirming…' : 'Confirm line-up and go to race control'}
          </Button>
          {toCalculate.length > 0 && (
            <span className="text-sm text-muted-foreground">
              Calculate the finals for {toCalculate.map((l) => l.className).join(', ')} before confirming.
            </span>
          )}
        </div>

        {isPending && <p role="status" className="text-sm text-muted-foreground">Loading…</p>}
        {isError && (
          <div role="alert" className="text-sm text-destructive">
            Could not load the finals line-ups.{' '}
            <button type="button" className="underline" onClick={() => refetch()}>Retry</button>
          </div>
        )}
        {classes.map((l) => (
          <div key={l.eventClassId} className="space-y-3">
            <FinalsLineupPanel eventId={eventId} eventClassId={l.eventClassId} canEdit hideCalculateAll onCarsChange={onCarsChange} />
            {l.calculated && <QualifyingTablePanel eventId={eventId} eventClassId={l.eventClassId} />}
          </div>
        ))}
        {dialog}
      </div>
    </div>
  );
}
