// Public overall qualifying standings, for a phone or a printout (opened from a QR code on the boards).
// Polls so the order moves as heats finish.
import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getQualifyingBoard, type QualifyingBoardTable } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import { BOARD_POLL_MS, parseEventParam } from '@/pages/boards/boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { QrLink } from '@/components/QrLink';
import { RoundHeaderCells, RoundPlaceCells, roundCount } from '@/components/QualifyingRounds';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { Printer } from 'lucide-react';


function ClassTable({ table }: { table: QualifyingBoardTable }) {
  const byPoints = table.rows.some((r) => r.points !== null);
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
                <th className="px-3 py-2">Pos</th>
                <th className="px-3 py-2">Driver</th>
                <RoundHeaderCells count={rounds} className="px-3 py-2 text-right" />
                {byPoints && <th className="px-3 py-2 text-right">Points</th>}
                <th className="px-3 py-2 text-right">Laps</th>
                <th className="px-3 py-2 text-right">Best lap</th>
              </tr>
            </thead>
            <tbody>
              {table.rows.map((row) => (
                <tr key={row.entryId} className="border-b last:border-0">
                  <td className="px-3 py-2 font-semibold tabular-nums">{row.position}</td>
                  <td className="px-3 py-2">{row.driverName}</td>
                  <RoundPlaceCells rounds={row.rounds} count={rounds} className="px-3 py-2 text-right tabular-nums" />
                  {byPoints && <td className="px-3 py-2 text-right tabular-nums">{row.points}</td>}
                  <td className="px-3 py-2 text-right tabular-nums">{row.lapsCompleted}</td>
                  <td className="px-3 py-2 text-right font-mono tabular-nums">{fmtMs(row.bestLapMs)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {byPoints && (
        <p className="text-xs text-muted-foreground">
          Ranked by low points: the lowest total over a driver's best rounds wins, and a tie goes to the most laps,
          then the quickest lap. R1, R2 and so on are the place in each round; a struck-through round was dropped.
        </p>
      )}
    </section>
  );
}

export default function QualifyingStandingsPage() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));

  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.qualifying(eventId),
    queryFn: () => getQualifyingBoard(eventId),
    refetchInterval: BOARD_POLL_MS,
  });

  useEffect(() => {
    const previous = document.title;
    document.title = data?.eventName ? `Qualifying — ${data.eventName}` : 'Qualifying standings';
    return () => {
      document.title = previous;
    };
  }, [data?.eventName]);

  if (isPending) {
    return <div className="p-8 text-sm text-muted-foreground">Loading qualifying standings…</div>;
  }

  if (isError && !data) {
    return (
      <div className="p-8 text-sm text-destructive">
        The standings could not be loaded. Check the connection; this page tries again on its own.
      </div>
    );
  }

  const path = data?.eventId ? `/qualifying?event=${data.eventId}` : '/qualifying';

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-6 sm:p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">Qualifying standings</h1>
        {data?.eventName && <p className="text-muted-foreground">{data.eventName}</p>}
        <p className="text-xs text-muted-foreground print:hidden">Updates by itself as heats finish.</p>
        <p className="hidden text-xs text-muted-foreground print:block">
          Printed {new Date().toLocaleString()}. Live standings: scan the code below.
        </p>
        <button
          type="button"
          onClick={() => window.print()}
          className="mt-2 inline-flex items-center gap-1.5 rounded border px-3 py-1.5 text-sm print:hidden"
        >
          <Printer className="h-4 w-4" aria-hidden="true" />
          Print
        </button>
      </header>

      {!data || data.tables.length === 0 ? (
        <p className="text-sm text-muted-foreground">No qualifying results yet.</p>
      ) : (
        data.tables.map((table) => <ClassTable key={table.eventClassId} table={table} />)
      )}

      <div className="flex justify-center pt-4">
        <QrLink path={path} caption="Scan to follow the standings on your phone" size={144} />
      </div>
      <div className="print:hidden">
        <PrivacyLink />
      </div>
    </div>
  );
}
