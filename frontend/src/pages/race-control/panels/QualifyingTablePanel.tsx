import { useQuery } from '@tanstack/react-query';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getQualifying } from '@/lib/raceControlApi';
import { fmtMs } from '@/lib/format';
import { RoundHeaderCells, RoundPlaceCells, roundCount } from '@/components/QualifyingRounds';
import { QueryError } from '@/components/QueryError';

/**
 * A class's overall qualifying table, the order its finals are calculated from. The finals are calculated, and
 * edited, in the finals line-up panel.
 */
export function QualifyingTablePanel({
  eventId,
  eventClassId,
}: {
  eventId: number;
  eventClassId: number;
}) {
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.qualifying(eventId),
    queryFn: () => getQualifying(eventId),
  });
  const table = data?.find((t) => t.eventClassId === eventClassId);
  const hasHeats = table?.rows.some((r) => r.heat != null) ?? false;
  const hasPoints = table?.rows.some((r) => r.points != null) ?? false;
  const hasChampPoints = table?.rows.some((r) => r.championshipPoints != null) ?? false;
  const rounds = table ? roundCount(table.rows) : 0;

  return (
    <section aria-label="Overall qualifying" className="flex flex-col gap-2">
      <h2 className="text-sm font-semibold">
        Overall qualifying{table ? `: ${table.className}` : ''}
      </h2>
      {isPending && <p role="status" className="text-sm text-muted-foreground">Loading…</p>}
      {isError && (
        <QueryError message="Could not load the qualifying table." onRetry={refetch} />
      )}
      {table && (
        <>
          <p className="text-sm text-muted-foreground">
            {table.heatsFinished} of {table.heatsTotal} qualifying{' '}
            {table.heatsTotal === 1 ? 'heat' : 'heats'} finished.{' '}
            {hasPoints
              ? 'Ranked by low points over the best rounds, then laps, then best lap. A struck-through round was dropped; one lost to a marshal penalty counts as the worst place.'
              : 'Ranked by total laps over all heats, then best lap.'}
          </p>
          {table.rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">No qualifying results yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  {hasHeats && <th className="w-12 py-1">Heat</th>}
                  <th className="w-12 py-1">Pos</th>
                  <th className="w-12 py-1">Car</th>
                  <th className="py-1">Driver</th>
                  <RoundHeaderCells count={rounds} className="w-10 py-1 text-right" />
                  {hasPoints && <th className="w-16 py-1 text-right">Points</th>}
                  <th className="w-16 py-1 text-right">Laps</th>
                  <th className="w-24 py-1 text-right">Best lap</th>
                  {hasChampPoints && <th className="w-20 py-1 text-right">Champ. pts</th>}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((row) => (
                  <tr key={row.entryId} className="border-b last:border-0">
                    {hasHeats && <td className="py-1 tabular-nums">{row.heat}</td>}
                    <td className="py-1 tabular-nums">{row.position}</td>
                    <td className="py-1 tabular-nums">{row.carNumber ?? '—'}</td>
                    <td className="py-1">{row.driverName}</td>
                    <RoundPlaceCells rounds={row.rounds} count={rounds} className="py-1 text-right tabular-nums" />
                    {hasPoints && <td className="py-1 text-right tabular-nums">{row.points}</td>}
                    <td className="py-1 text-right tabular-nums">{row.lapsCompleted}</td>
                    <td className="py-1 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
                    {hasChampPoints && <td className="py-1 text-right tabular-nums">{row.championshipPoints ?? '—'}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}
