// The officials' event printouts, opened from the Print page: the summary of one round, the final results, or the
// meeting pack. Names are the real ones (not the public name setting), and each heat shows where the driver
// started. One class per page, A4 portrait. Lives outside the race control layout, whose fixed height would clip
// a long printout.
import { useEffect, useMemo } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, Printer } from 'lucide-react';
import { useEventResults } from '@/hooks/race-control/useEventResults';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getQualifying, type EventResultsRace, type QualifyingTable } from '@/lib/raceControlApi';
import { finalsInReadingOrder, groupByClass, heatTitle, qualifyingRounds, racesOfRound } from '@/lib/eventPrint';
import { fmtMs } from '@/lib/format';
import { HeatResultTable } from '@/components/print/HeatResultTable';
import { SheetHeader } from '@/components/print/SheetHeader';
import { RoundHeaderCells, RoundPlaceCells, roundCount } from '@/components/QualifyingRounds';
import { Button } from '@/components/ui/button';

type Round = { type: 'PRACTICE' | 'QUALIFIER'; number: number };

function HeatBlock({ race }: { race: EventResultsRace }) {
  return (
    <section className="mb-6 break-inside-avoid" aria-label={heatTitle(race)}>
      <h3 className="mb-1 text-base font-semibold">{heatTitle(race)}</h3>
      <HeatResultTable race={race} />
    </section>
  );
}

function StandingsBlock({ table }: { table: QualifyingTable }) {
  const byPoints = table.rows.some((r) => r.points !== null);
  const rounds = roundCount(table.rows);
  return (
    <section className="mb-6 break-inside-avoid" aria-label="Qualifying standings">
      <h3 className="mb-1 text-base font-semibold">Qualifying standings</h3>
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b-2 border-foreground">
            <th scope="col" className="w-10 py-2 pr-4 text-left">Pos</th>
            <th scope="col" className="py-2 pr-4 text-left">Driver</th>
            <th scope="col" className="py-2 pr-4 text-left">Car</th>
            <RoundHeaderCells count={rounds} className="py-2 pr-4 text-right" />
            {byPoints && <th scope="col" className="py-2 pr-4 text-right">Points</th>}
            <th scope="col" className="py-2 pr-4 text-right">Laps</th>
            <th scope="col" className="py-2 text-right">Best Lap</th>
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row) => (
            <tr key={row.entryId} className="border-b border-border/50 print:break-inside-avoid">
              <td className="py-1.5 pr-4 font-semibold">{row.position}</td>
              <td className="py-1.5 pr-4">{row.driverName}</td>
              <td className="py-1.5 pr-4 text-muted-foreground">{row.carNumber ?? '—'}</td>
              <RoundPlaceCells rounds={row.rounds} count={rounds} className="py-1.5 pr-4 text-right font-mono" />
              {byPoints && <td className="py-1.5 pr-4 text-right font-mono">{row.points}</td>}
              <td className="py-1.5 pr-4 text-right font-mono">{row.lapsCompleted}</td>
              <td className="py-1.5 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

/** Which of the parts a meeting pack holds, from the address (`parts=standings,rounds,finals`; all when absent). */
function packParts(raw: string | null) {
  const wanted = raw === null ? ['standings', 'rounds', 'finals'] : raw.split(',');
  return {
    standings: wanted.includes('standings'),
    rounds: wanted.includes('rounds'),
    finals: wanted.includes('finals'),
  };
}

export default function PrintEventResultsPage() {
  const { eventId: eventIdStr } = useParams<{ eventId: string }>();
  const eventId = Number(eventIdStr);
  const validId = Number.isFinite(eventId) && eventId > 0;
  const [searchParams] = useSearchParams();
  const view = searchParams.get('view') === 'round' || searchParams.get('view') === 'finals'
    ? (searchParams.get('view') as 'round' | 'finals')
    : 'pack';
  const roundType = searchParams.get('type') === 'PRACTICE' ? 'PRACTICE' : 'QUALIFIER';
  const roundNumber = Number(searchParams.get('round')) || 1;
  const parts = useMemo(() => packParts(searchParams.get('parts')), [searchParams]);
  const printedAt = useMemo(() => new Date().toLocaleString(), []);

  const results = useEventResults(validId ? eventId : null);
  const standings = useQuery({
    queryKey: raceControlQueryKeys.qualifying(eventId),
    queryFn: () => getQualifying(eventId),
    enabled: validId && view === 'pack' && parts.standings,
    staleTime: 30_000,
  });

  const title = view === 'round'
    ? `Round summary — ${roundType === 'PRACTICE' ? 'Practice' : 'Qualifier'} ${roundNumber}`
    : view === 'finals' ? 'Final results' : 'Meeting results';
  useEffect(() => {
    const previous = document.title;
    document.title = results.data ? `${title} — ${results.data.eventName}` : title;
    return () => {
      document.title = previous;
    };
  }, [title, results.data]);

  if (!validId) {
    return <div className="p-8 text-sm text-destructive">There is no such event.</div>;
  }
  if (results.isPending) {
    return <div className="p-8 text-sm text-muted-foreground">Loading results…</div>;
  }
  if (results.isError || !results.data) {
    return <div className="p-8 text-sm text-destructive">Could not load the results.</div>;
  }

  const data = results.data;
  const branding = data.races[0]?.result.clubBranding;
  const round: Round = { type: roundType, number: roundNumber };
  const classes = groupByClass(data.races);
  const standingsByClass = new Map((standings.data ?? []).map((t) => [t.eventClassId, t]));

  const sections = classes.map((group) => {
    const heats = view === 'round' ? racesOfRound(group.races, round.type, round.number) : [];
    const finals = view === 'finals' || (view === 'pack' && parts.finals) ? finalsInReadingOrder(group.races) : [];
    const packRounds = view === 'pack' && parts.rounds ? qualifyingRounds(group.races) : [];
    const table = view === 'pack' && parts.standings ? standingsByClass.get(group.eventClassId) : undefined;
    const empty = heats.length === 0 && finals.length === 0 && packRounds.length === 0 && !table;
    return { group, heats, finals, packRounds, table, empty };
  }).filter((s) => !s.empty);

  return (
    <div className="mx-auto max-w-3xl p-8 print:p-4">
      <div className="mb-4 flex items-center gap-2 print:hidden">
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/race-control/event/${eventId}/print`}>
            <ChevronLeft className="mr-1 h-4 w-4" aria-hidden="true" />
            Back to Print
          </Link>
        </Button>
        <Button variant="outline" size="sm" onClick={() => window.print()}>
          <Printer className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Print
        </Button>
      </div>

      {sections.length === 0 ? (
        <>
          <SheetHeader title={title} eventName={data.eventName} eventDate={data.eventDate} branding={branding} />
          <p className="text-sm text-muted-foreground">Nothing has finished yet that belongs on this sheet.</p>
        </>
      ) : (
        sections.map(({ group, heats, finals, packRounds, table }) => (
          <article
            key={group.eventClassId}
            aria-label={group.className}
            className="print:break-after-page print:last:break-after-auto"
          >
            <SheetHeader
              title={`${title} — ${group.className}`}
              eventName={data.eventName}
              eventDate={data.eventDate}
              branding={branding}
            />
            {table && <StandingsBlock table={table} />}
            {packRounds.map((n) => (
              <div key={n}>
                <h2 className="mb-2 text-lg font-semibold">Qualifier {n}</h2>
                {racesOfRound(group.races, 'QUALIFIER', n).map((race) => <HeatBlock key={race.raceId} race={race} />)}
              </div>
            ))}
            {heats.map((race) => <HeatBlock key={race.raceId} race={race} />)}
            {finals.length > 0 && (
              <div>
                {view === 'pack' && <h2 className="mb-2 text-lg font-semibold">Finals</h2>}
                {finals.map((race) => <HeatBlock key={race.raceId} race={race} />)}
              </div>
            )}
            <p className="hidden text-xs text-muted-foreground print:block">Printed {printedAt}.</p>
          </article>
        ))
      )}
    </div>
  );
}
