import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getPublicChampionshipStandings } from '@/lib/raceControlApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import PrivacyLink from '@/components/layout/PrivacyLink';

export default function PublicChampionshipPage() {
  const { id } = useParams<{ id: string }>();
  const championshipId = Number(id);

  const { data: rows, isLoading, isError } = useQuery({
    queryKey: publicQueryKeys.championship(championshipId),
    queryFn: () => getPublicChampionshipStandings(championshipId),
    enabled: Number.isFinite(championshipId) && championshipId > 0,
  });

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

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">Championship Standings</h1>
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b-2 border-foreground">
            <th className="text-left py-2 pr-4 w-10">Pos</th>
            <th className="text-left py-2 pr-4">Driver</th>
            <th className="text-right py-2 pr-4">Points</th>
            <th className="text-left py-2">Rounds</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => (
            <tr key={row.driverId} className="border-b border-border/50">
              <td className="py-2 pr-4 font-mono">{idx + 1}</td>
              <td className="py-2 pr-4">{row.displayName}</td>
              <td className="py-2 pr-4 text-right font-mono font-bold">{row.totalPoints}</td>
              <td className="py-2 flex flex-wrap gap-1">
                {row.rounds.map((r) => (
                  <span
                    key={r.roundNumber}
                    className={`text-xs font-mono px-1 rounded ${
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
      <PrivacyLink />
    </div>
  );
}
