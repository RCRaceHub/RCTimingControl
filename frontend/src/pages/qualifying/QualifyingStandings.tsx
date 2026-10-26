// The overall qualifying tables, shown on the Results page's Qualifying tab.
import { useQuery } from '@tanstack/react-query';
import { getQualifyingBoard, type QualifyingBoardTable } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import { BOARD_POLL_MS } from '@/pages/boards/boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { RoundHeaderCells, RoundPlaceCells, roundCount } from '@/components/QualifyingRounds';

function ClassTable({ table }: { table: QualifyingBoardTable }) {
  const byPoints = table.rows.some((r) => r.points !== null);
  const hasChampPoints = table.rows.some((r) => r.championshipPoints != null);
  const hasHeats = table.rows.some((r) => r.heat != null);
  const rounds = roundCount(table.rows);
  return (
    <section className="space-y-2 break-inside-avoid print:break-after-page print:last:break-after-auto">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-lg font-semibold">{table.className}</h2>
        <p className="text-xs text-muted-foreground">
          {table.heatsFinished} of {table.heatsTotal} heats finished
        </p>
      </div>
      {table.rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No qualifying results yet.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm" aria-label={`${table.className} qualifying`}>
            <thead>
              <tr className="border-b bg-muted/50 text-left text-muted-foreground">
                {hasHeats && <th className="px-3 py-2">Heat</th>}
                <th className="px-3 py-2">Pos</th>
                <th className="px-3 py-2">Car</th>
                <th className="px-3 py-2">Driver</th>
                <RoundHeaderCells count={rounds} className="px-3 py-2 text-right" />
                {byPoints && <th className="px-3 py-2 text-right">Points</th>}
                <th className="px-3 py-2 text-right">Laps</th>
                <th className="px-3 py-2 text-right">Best lap</th>
                {hasChampPoints && <th className="px-3 py-2 text-right">Champ. pts</th>}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((row) => (
                <tr key={row.entryId} className="border-b last:border-0">
                  {hasHeats && <td className="px-3 py-2 tabular-nums">{row.heat}</td>}
                  <td className="px-3 py-2 font-semibold tabular-nums">{row.position}</td>
                  <td className="px-3 py-2 tabular-nums">{row.carNumber ?? '—'}</td>
                  <td className="px-3 py-2">{row.driverName}</td>
                  <RoundPlaceCells rounds={row.rounds} count={rounds} className="px-3 py-2 text-right tabular-nums" />
                  {byPoints && <td className="px-3 py-2 text-right tabular-nums">{row.points}</td>}
                  <td className="px-3 py-2 text-right tabular-nums">{row.lapsCompleted}</td>
                  <td className="px-3 py-2 text-right font-mono tabular-nums">{fmtMs(row.bestLapMs)}</td>
                  {hasChampPoints && <td className="px-3 py-2 text-right tabular-nums">{row.championshipPoints ?? '—'}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {byPoints && (
        <p className="text-xs text-muted-foreground">
          Ranked by low points: the lowest total over a driver's best rounds wins, and a tie goes to the most laps,
          then the quickest lap. R1, R2 and so on are the place in each round; a struck-through round was dropped, and a round lost to a marshal penalty counts as the worst place.
        </p>
      )}
    </section>
  );
}

/** The qualifying tables for the Results page's Qualifying tab; polls so the order moves as heats finish. */
export function QualifyingStandings({ eventId }: { eventId: number | null }) {
  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.qualifying(eventId),
    queryFn: () => getQualifyingBoard(eventId),
    refetchInterval: BOARD_POLL_MS,
  });

  if (isPending) {
    return <p className="text-sm text-muted-foreground">Loading qualifying standings…</p>;
  }
  if (isError && !data) {
    return (
      <p className="text-sm text-destructive">
        The standings could not be loaded. Check the connection; this page tries again on its own.
      </p>
    );
  }
  if (!data || data.tables.length === 0) {
    return <p className="text-sm text-muted-foreground">No qualifying results yet.</p>;
  }
  return (
    <div className="space-y-6">
      {data.tables.map((table) => <ClassTable key={table.eventClassId} table={table} />)}
    </div>
  );
}
