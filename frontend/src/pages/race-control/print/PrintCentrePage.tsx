// The Print page in race control: the one place to find every printable sheet for the event: a heat result, a round
// summary, the final results, a meeting pack, or a practice session's results.
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { useEventResults } from '@/hooks/race-control/useEventResults';
import { qualifyingRounds } from '@/lib/eventPrint';
import type { EventResultsRace } from '@/lib/raceControlApi';
import { listSessions } from '@/lib/practiceApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { Button } from '@/components/ui/button';

const PARTS = [
  { key: 'standings', label: 'Qualifying standings' },
  { key: 'rounds', label: 'Round summaries (every qualifying heat)' },
  { key: 'finals', label: 'Final results' },
] as const;

/** What a heat or final is called on its button, such as "Buggy heat 2" or "Buggy final A". */
function raceLabel(race: EventResultsRace): string {
  return race.roundType === 'FINAL'
    ? `${race.className} final ${race.finalLetter ?? ''}`.trim()
    : `${race.className} heat ${race.heatNumber}`;
}

/** The finished races grouped under "Qualifier 1", "Finals" and so on, in run order. */
function groupByRound(races: EventResultsRace[]): { label: string; races: EventResultsRace[] }[] {
  const groups = new Map<string, { label: string; races: EventResultsRace[] }>();
  for (const race of races) {
    const key = `${race.roundType}-${race.roundType === 'FINAL' ? 0 : race.roundNumber}`;
    const label = race.roundType === 'FINAL' ? 'Finals' : race.roundType === 'PRACTICE' ? `Practice ${race.roundNumber}` : `Qualifier ${race.roundNumber}`;
    const group = groups.get(key) ?? { label, races: [] };
    group.races.push(race);
    groups.set(key, group);
  }
  return [...groups.values()];
}

export default function PrintCentrePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const { data, isPending, isError } = useEventResults(Number(eventId) || null);
  const sessions = useQuery({
    queryKey: raceControlQueryKeys.practice.sessions(),
    queryFn: () => listSessions(20),
  });
  const [parts, setParts] = useState<Record<string, boolean>>({ standings: true, rounds: true, finals: true });
  const base = `/race-control/print/event/${eventId}`;

  if (isPending) {
    return <div className="p-6 text-sm text-muted-foreground">Loading…</div>;
  }
  if (isError || !data) {
    return <div className="p-6 text-sm text-destructive">Could not load the finished races.</div>;
  }

  const rounds = qualifyingRounds(data.races);
  const hasFinals = data.races.some((r) => r.roundType === 'FINAL');
  const practiceForEvent = (sessions.data ?? []).filter((x) => x.eventId === Number(eventId));
  const chosen = PARTS.filter((p) => parts[p.key]).map((p) => p.key).join(',');

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-2xl space-y-8 p-6">
        <header>
          <h1 className="flex items-center gap-2 text-xl font-semibold">
            <Printer className="h-5 w-5" aria-hidden="true" />
            Print results
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Sheets for the officials, with real names and where each driver started. Each opens in a new page; use
            Print there (or Save as PDF).
          </p>
        </header>

        <section aria-label="Heat results" className="space-y-2">
          <h2 className="font-semibold">Heat results</h2>
          {data.races.length === 0 ? (
            <p className="text-sm text-muted-foreground">No race has finished yet.</p>
          ) : (
            groupByRound(data.races).map((group) => (
              <div key={group.label} className="space-y-1">
                <h3 className="text-sm text-muted-foreground">{group.label}</h3>
                <div className="flex flex-wrap gap-2">
                  {group.races.map((race) => (
                    <Button key={race.raceId} variant="outline" size="sm" asChild>
                      <Link to={`/race-control/event/${eventId}/results/${race.raceId}`} target="_blank">
                        {raceLabel(race)}
                      </Link>
                    </Button>
                  ))}
                </div>
              </div>
            ))
          )}
        </section>

        <section aria-label="Round summary" className="space-y-2">
          <h2 className="font-semibold">Round summary</h2>
          {rounds.length === 0 ? (
            <p className="text-sm text-muted-foreground">No qualifying round has finished a heat yet.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {rounds.map((n) => (
                <Button key={n} variant="outline" size="sm" asChild>
                  <Link to={`${base}?view=round&type=QUALIFIER&round=${n}`}>Qualifier {n}</Link>
                </Button>
              ))}
            </div>
          )}
        </section>

        <section aria-label="Final results" className="space-y-2">
          <h2 className="font-semibold">Final results</h2>
          {hasFinals ? (
            <Button variant="outline" size="sm" asChild>
              <Link to={`${base}?view=finals`}>Final results, every class</Link>
            </Button>
          ) : (
            <p className="text-sm text-muted-foreground">No final has finished yet.</p>
          )}
        </section>

        <section aria-label="Meeting pack" className="space-y-2">
          <h2 className="font-semibold">Meeting pack</h2>
          <p className="text-sm text-muted-foreground">One print job, class by class, with the parts you tick.</p>
          <div className="space-y-1">
            {PARTS.map((p) => (
              <label key={p.key} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={parts[p.key]}
                  onChange={(e) => setParts({ ...parts, [p.key]: e.target.checked })}
                />
                {p.label}
              </label>
            ))}
          </div>
          <Button size="sm" asChild={chosen !== ''} disabled={chosen === ''}>
            {chosen === '' ? <span>Open meeting pack</span> : <Link to={`${base}?view=pack&parts=${chosen}`}>Open meeting pack</Link>}
          </Button>
        </section>

        <section aria-label="Practice results" className="space-y-2">
          <h2 className="font-semibold">Practice results</h2>
          {practiceForEvent.length === 0 ? (
            <p className="text-sm text-muted-foreground">No practice session is linked to this event.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {practiceForEvent.map((x) => (
                <Button key={x.id} variant="outline" size="sm" asChild>
                  <Link to={`/race-control/practice/${x.id}/print`}>{x.name}</Link>
                </Button>
              ))}
              <Button variant="outline" size="sm" asChild>
                <Link to="/race-control/practice/day">Practice day view</Link>
              </Button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
