// The Print page in race control: which round summary, the final results, or a meeting pack to open as a sheet.
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Printer } from 'lucide-react';
import { useEventResults } from '@/hooks/race-control/useEventResults';
import { qualifyingRounds } from '@/lib/eventPrint';
import { Button } from '@/components/ui/button';

const PARTS = [
  { key: 'standings', label: 'Qualifying standings' },
  { key: 'rounds', label: 'Round summaries (every qualifying heat)' },
  { key: 'finals', label: 'Final results' },
] as const;

export default function PrintCentrePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const { data, isPending, isError } = useEventResults(Number(eventId) || null);
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
            Print there (or Save as PDF). The heat result is printed from a finished race in the cockpit.
          </p>
        </header>

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
      </div>
    </div>
  );
}
