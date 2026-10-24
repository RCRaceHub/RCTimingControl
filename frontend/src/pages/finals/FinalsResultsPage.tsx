// Public finals results, for a phone or a printout: each class's A, B and C finals, best first. Polls so a final
// appears as it finishes.
import { useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { getFinalsBoard, type FinalLegs, type FinalsBoardClass, type FinalsBoardFinal } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import { BOARD_POLL_MS, parseEventParam } from '@/pages/boards/boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { QrLink } from '@/components/QrLink';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { Button } from '@/components/ui/button';
import { DnfBadge } from '@/components/DnfBadge';

const TH = 'px-3 py-2';

/** The A final run in legs: points in each leg (a dropped leg struck through) and the total, lowest first. */
function LegsTable({ legs }: { legs: FinalLegs }) {
  const legNumbers = Array.from({ length: legs.rows[0]?.legPoints.length ?? 0 }, (_, i) => i + 1);
  return (
    <>
      <table className="w-full text-sm" aria-label="Final A overall">
        <thead>
          <tr className="border-b bg-muted/50 text-left text-muted-foreground">
            <th className={TH}>Pos</th>
            <th className={TH}>Car</th>
            <th className={TH}>Driver</th>
            {legNumbers.map((n) => <th key={n} className={`${TH} text-right`}>Leg {n}</th>)}
            <th className={`${TH} text-right`}>Points</th>
          </tr>
        </thead>
        <tbody>
          {legs.rows.map((row) => (
            <tr key={row.entryId} className="border-b last:border-0">
              <td className="px-3 py-2 font-semibold tabular-nums">{row.position}</td>
              <td className="px-3 py-2 tabular-nums">{row.carNumber ?? '—'}</td>
              <td className="px-3 py-2">{row.driverName}</td>
              {row.legPoints.map((points, i) => (
                <td
                  key={i}
                  className={`px-3 py-2 text-right tabular-nums ${row.counted[i] ? '' : 'text-muted-foreground line-through'}`}
                >
                  {points}
                </td>
              ))}
              <td className="px-3 py-2 text-right font-semibold tabular-nums">{row.points}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="px-3 py-2 text-xs text-muted-foreground">
        {legs.complete
          ? `Best ${legs.legsCounted || legs.legs} of ${legs.legs} legs count; lowest points wins.`
          : `After ${legs.legsFinished} of ${legs.legs} legs; lowest points wins.`}
      </p>
    </>
  );
}

function ResultsTable({ final }: { final: FinalsBoardFinal }) {
  const hasPoints = final.results.some((r) => r.championshipPoints != null);
  return (
    <table className="w-full text-sm" aria-label={`Final ${final.letter}`}>
      <thead>
        <tr className="border-b bg-muted/50 text-left text-muted-foreground">
          <th className={TH}>Pos</th>
          <th className={TH}>Car</th>
          <th className={TH}>Driver</th>
          <th className={`${TH} text-right`}>Laps</th>
          <th className={`${TH} text-right`}>Time</th>
          <th className={`${TH} text-right`}>Best lap</th>
          {hasPoints && <th className={`${TH} text-right`}>Champ. pts</th>}
        </tr>
      </thead>
      <tbody>
        {final.results.map((row) => (
          <tr key={row.entryId} className="border-b last:border-0">
            <td className="px-3 py-2 font-semibold tabular-nums">{row.position}</td>
            <td className="px-3 py-2 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="px-3 py-2">{row.driverName}</td>
            <td className="px-3 py-2 text-right tabular-nums">{row.lapsCompleted}<DnfBadge dnf={row.dnf} /></td>
            <td className="px-3 py-2 text-right font-mono tabular-nums">{fmtMs(row.totalTimeMs)}</td>
            <td className="px-3 py-2 text-right font-mono tabular-nums">{fmtMs(row.bestLapMs)}</td>
            {hasPoints && <td className="px-3 py-2 text-right tabular-nums">{row.championshipPoints ?? '—'}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function FinalBlock({ final }: { final: FinalsBoardFinal }) {
  const legs = final.legs && final.legs.rows.length > 0 ? final.legs : null;
  const hasOrder = legs !== null || final.results.length > 0;
  return (
    <div className="space-y-1">
      <h3 className="font-semibold">Final {final.letter}</h3>
      {!hasOrder ? (
        <p className="text-sm text-muted-foreground">
          {final.abandoned ? 'Abandoned.' : final.status === 'PENDING' || final.status === 'GRID' ? 'Not run yet.' : 'No result yet.'}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          {legs ? <LegsTable legs={legs} /> : <ResultsTable final={final} />}
        </div>
      )}
    </div>
  );
}

function ClassSection({ cls }: { cls: FinalsBoardClass }) {
  return (
    <section className="space-y-3 break-inside-avoid print:break-after-page print:last:break-after-auto">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-lg font-semibold">{cls.className}</h2>
        <p className="text-xs text-muted-foreground">
          {cls.finalsFinished} of {cls.finalsTotal} finals finished
        </p>
      </div>
      {cls.finals.map((final) => <FinalBlock key={final.letter} final={final} />)}
    </section>
  );
}

export default function FinalsResultsPage() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const printedAt = useMemo(() => new Date().toLocaleString(), []);

  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.finals(eventId),
    queryFn: () => getFinalsBoard(eventId),
    refetchInterval: BOARD_POLL_MS,
  });

  useEffect(() => {
    const previous = document.title;
    document.title = data?.eventName ? `Finals — ${data.eventName}` : 'Finals results';
    return () => {
      document.title = previous;
    };
  }, [data?.eventName]);

  if (isPending) {
    return <div className="p-8 text-sm text-muted-foreground">Loading finals results…</div>;
  }

  if (isError && !data) {
    return (
      <div className="p-8 text-sm text-destructive">
        The results could not be loaded. Check the connection; this page tries again on its own.
      </div>
    );
  }

  const path = data?.eventId ? `/finals?event=${data.eventId}` : '/finals';

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-6 sm:p-8 print:p-4">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">Finals results</h1>
        {data?.eventName && <p className="text-muted-foreground">{data.eventName}</p>}
        <p className="text-xs text-muted-foreground print:hidden">Updates by itself as finals finish.</p>
        <p className="hidden text-xs text-muted-foreground print:block">
          Printed {printedAt}. For the live results, scan the code.
        </p>
        <div className="print:hidden">
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Print
          </Button>
        </div>
      </header>

      {!data || data.classes.length === 0 ? (
        <p className="text-sm text-muted-foreground">No finals yet.</p>
      ) : (
        <div className="space-y-6">
          {data.classes.map((cls) => <ClassSection key={cls.eventClassId} cls={cls} />)}
        </div>
      )}

      <div className="flex justify-center pt-4">
        <QrLink path={path} caption="Scan to follow the finals on your phone" size={144} />
      </div>
      <PrivacyLink />
    </div>
  );
}
