import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getQualifying, seedFinalsFromQualifying } from '@/lib/raceControlApi';
import { fmtMs } from '@/lib/format';
import { getApiErrorMessage } from '@/lib/errors';
import { Button } from '@/components/ui/button';

/**
 * A class's overall qualifying table, the order its finals are seeded from, with a button to seed the finals
 * again after a heat was re-run or corrected. The finals are also seeded by themselves when the last
 * qualifying heat finishes.
 */
export function QualifyingTablePanel({
  eventId,
  eventClassId,
  canSeed,
}: {
  eventId: number;
  eventClassId: number;
  canSeed: boolean;
}) {
  const queryClient = useQueryClient();
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.qualifying(eventId),
    queryFn: () => getQualifying(eventId),
  });
  const table = data?.find((t) => t.eventClassId === eventClassId);
  const hasPoints = table?.rows.some((r) => r.points != null) ?? false;

  const seed = useMutation({
    mutationFn: () => seedFinalsFromQualifying(eventId, eventClassId),
    onSuccess: () => {
      toast.success('Finals seeded from the qualifying order');
      queryClient.invalidateQueries({ queryKey: ['race-control'] });
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'Could not seed the finals')),
  });

  return (
    <section aria-label="Overall qualifying" className="flex flex-col gap-2">
      <h2 className="text-sm font-semibold">
        Overall qualifying{table ? `: ${table.className}` : ''}
      </h2>
      {isPending && <p role="status" className="text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <div role="alert" className="text-sm text-destructive">
          Could not load the qualifying table.{' '}
          <button type="button" className="underline" onClick={() => refetch()}>Retry</button>
        </div>
      )}
      {table && (
        <>
          <p className="text-sm text-muted-foreground">
            {table.heatsFinished} of {table.heatsTotal} qualifying{' '}
            {table.heatsTotal === 1 ? 'heat' : 'heats'} finished.{' '}
            {hasPoints
              ? 'Ranked by low points over the best rounds, then laps, then best lap.'
              : 'Ranked by total laps over all heats, then best lap.'}
          </p>
          {table.rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">No qualifying results yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  <th className="w-12 py-1">Pos</th>
                  <th className="py-1">Driver</th>
                  {hasPoints && <th className="w-16 py-1 text-right">Points</th>}
                  <th className="w-16 py-1 text-right">Laps</th>
                  <th className="w-24 py-1 text-right">Best lap</th>
                </tr>
              </thead>
              <tbody>
                {table.rows.map((row) => (
                  <tr key={row.entryId} className="border-b last:border-0">
                    <td className="py-1 tabular-nums">{row.position}</td>
                    <td className="py-1">{row.driverName}</td>
                    {hasPoints && <td className="py-1 text-right tabular-nums">{row.points}</td>}
                    <td className="py-1 text-right tabular-nums">{row.lapsCompleted}</td>
                    <td className="py-1 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {canSeed && (
            <div className="flex items-center gap-3">
              <Button
                variant="outline"
                className="w-fit"
                onClick={() => seed.mutate()}
                disabled={seed.isPending || table.finalsStarted || table.heatsFinished < table.heatsTotal || table.rows.length === 0}
              >
                {seed.isPending ? 'Seeding…' : 'Seed finals from this order'}
              </Button>
              {!table.finalsStarted && table.heatsFinished < table.heatsTotal && (
                <span className="text-sm text-muted-foreground">Finish or abandon every qualifying heat first.</span>
              )}
              {table.finalsStarted && (
                <span className="text-sm text-muted-foreground">A final has started, so the finals can't be seeded again.</span>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}
