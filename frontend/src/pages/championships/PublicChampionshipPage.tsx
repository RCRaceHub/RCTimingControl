// Public championship standings, one table per class, laid out to print on A4 portrait with a class to a page.
import { useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { getPublicChampionshipInfo, getPublicChampionshipStandings } from '@/lib/raceControlApi';
import type { StandingsRowDto } from '@/lib/adminApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { QrLink } from '@/components/QrLink';
import { Button } from '@/components/ui/button';

type ClassGroup = { racingClassId: number; name: string; rows: StandingsRowDto[] };

/** The rows split by class in the order the classes are named; a class the info doesn't name is headed "Class N". */
function groupByClass(rows: StandingsRowDto[], names: Map<number, string>): ClassGroup[] {
  const groups = new Map<number, ClassGroup>();
  for (const row of rows) {
    let group = groups.get(row.racingClassId);
    if (!group) {
      group = {
        racingClassId: row.racingClassId,
        name: names.get(row.racingClassId) ?? `Class ${row.racingClassId}`,
        rows: [],
      };
      groups.set(row.racingClassId, group);
    }
    group.rows.push(row);
  }
  return [...groups.values()].sort((a, b) => a.name.localeCompare(b.name));
}

export default function PublicChampionshipPage() {
  const { id } = useParams<{ id: string }>();
  const championshipId = Number(id);
  const validId = Number.isFinite(championshipId) && championshipId > 0;
  const printedAt = useMemo(() => new Date().toLocaleString(), []);

  const { data: rows, isLoading, isError } = useQuery({
    queryKey: publicQueryKeys.championship(championshipId),
    queryFn: () => getPublicChampionshipStandings(championshipId),
    enabled: validId,
  });
  // The names only head the tables: without them the standings still show
  const info = useQuery({
    queryKey: publicQueryKeys.championshipInfo(championshipId),
    queryFn: () => getPublicChampionshipInfo(championshipId),
    enabled: validId,
  });

  const title = info.data?.name ? `${info.data.name} standings` : 'Championship Standings';
  useEffect(() => {
    const previous = document.title;
    document.title = title;
    return () => {
      document.title = previous;
    };
  }, [title]);

  if (isLoading) {
    return <div className="p-8 text-sm text-muted-foreground">Loading standings…</div>;
  }

  if (isError || !rows) {
    return (
      <div className="p-8 text-sm text-destructive">
        Championship standings are not available.
      </div>
    );
  }

  const names = new Map((info.data?.classes ?? []).map((c) => [c.racingClassId, c.name]));
  const groups = groupByClass(rows, names);

  return (
    <div className="mx-auto max-w-4xl p-8 print:p-4">
      <header className="mb-6 space-y-1">
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="hidden text-xs text-muted-foreground print:block">
          Printed {printedAt}. For the live standings, scan the code.
        </p>
        <div className="print:hidden">
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Print
          </Button>
        </div>
      </header>

      {groups.length === 0 && <p className="text-sm text-muted-foreground">No results have counted yet.</p>}

      {groups.map((group) => (
        <section
          key={group.racingClassId}
          aria-label={group.name}
          className="mb-8 print:break-after-page print:last:break-after-auto"
        >
          <h2 className="mb-2 text-lg font-semibold">{group.name}</h2>
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b-2 border-foreground">
                <th className="w-10 py-2 pr-4 text-left">Pos</th>
                <th className="py-2 pr-4 text-left">Driver</th>
                <th className="py-2 pr-4 text-right">Points</th>
                <th className="py-2 text-left">Rounds</th>
              </tr>
            </thead>
            <tbody>
              {group.rows.map((row, idx) => (
                <tr key={row.driverId} className="border-b border-border/50 print:break-inside-avoid">
                  <td className="py-2 pr-4 font-mono">{idx + 1}</td>
                  <td className="py-2 pr-4">{row.displayName}</td>
                  <td className="py-2 pr-4 text-right font-mono font-bold">{row.totalPoints}</td>
                  <td className="flex flex-wrap gap-1 py-2">
                    {row.rounds.map((r) => (
                      <span
                        key={r.roundNumber}
                        className={`rounded px-1 font-mono text-xs ${
                          r.dropped
                            ? 'text-muted-foreground line-through'
                            : r.excluded
                            ? 'text-destructive'
                            : ''
                        }`}
                        title={r.eventName}
                      >
                        {r.points}
                      </span>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-2 text-xs text-muted-foreground">
            Each number is the points from one round. A struck-through round was dropped from the total.
          </p>
        </section>
      ))}

      <div className="flex justify-center pt-4">
        <QrLink path={`/championships/${championshipId}`} caption="Scan to follow the standings on your phone" size={144} />
      </div>
      <PrivacyLink />
    </div>
  );
}
