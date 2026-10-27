import type { ResultRow } from '@/lib/raceControlApi';
import { fmtMs, resultGapLabel } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';
import { DnfBadge } from '@/components/DnfBadge';

export function BoardResultsTable({ results }: { results: ResultRow[] }) {
  if (results.length === 0) {
    return <p className="text-2xl text-slate-400">No results recorded for this race.</p>;
  }
  const hasPoints = results.some((r) => r.points != null);
  const hasChampionshipPoints = results.some((r) => r.championshipPoints != null);
  const leaderLaps = Math.max(...results.map((r) => r.lapsCompleted));
  return (
    <table className="w-full text-2xl lg:text-4xl" aria-label="Results">
      <thead>
        <tr className="border-b border-slate-700 text-left text-lg text-slate-400 lg:text-2xl">
          <th className="py-2 pr-4">Pos</th>
          <th className="py-2 pr-4">Driver</th>
          <th className="py-2 pr-4">Car</th>
          <th className="py-2 pr-4 text-right">Laps</th>
          <th className="py-2 pr-4 text-right">Total</th>
          <th className="py-2 pr-4 text-right">Best lap</th>
          <th className={`py-2 text-right ${hasPoints ? 'pr-4' : ''}`}>Gap</th>
          {hasPoints && <th className="py-2 text-right">Points</th>}
          {hasChampionshipPoints && <th className="py-2 pl-4 text-right">Champ. pts</th>}
        </tr>
      </thead>
      <tbody>
        {results.map((row) => (
          <tr key={row.entryId} className="border-b border-slate-800">
            <td className="py-3 pr-4 font-bold tabular-nums">{row.position}</td>
            <td className="py-3 pr-4 font-semibold">{row.driverName}{!row.dnf && <FinishedFlag />}</td>
            <td className="py-3 pr-4 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{row.lapsCompleted}<DnfBadge dnf={row.dnf} className="ml-3 text-lg font-semibold text-red-400" /></td>
            <td className="py-3 pr-4 text-right tabular-nums">{row.totalTimeMs > 0 ? fmtMs(row.totalTimeMs) : '—'}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.bestLapMs)}</td>
            <td className={`py-3 text-right tabular-nums text-slate-300 ${hasPoints ? 'pr-4' : ''}`}>{resultGapLabel(row, leaderLaps)}</td>
            {hasPoints && <td className="py-3 text-right font-semibold tabular-nums">{row.points ?? '—'}</td>}
            {hasChampionshipPoints && (
              <td className="py-3 pl-4 text-right font-semibold tabular-nums">{row.championshipPoints ?? '—'}</td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
