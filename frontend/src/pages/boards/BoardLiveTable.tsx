import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { fmtMs } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';

function gap(row: LiveTimingRowDto): string {
  if (row.position === 1) return '—';
  if (row.lapsDown > 0) return `+${row.lapsDown} ${row.lapsDown === 1 ? 'lap' : 'laps'}`;
  return row.gapToLeaderMs !== null ? `+${fmtMs(row.gapToLeaderMs)}` : '—';
}

export function BoardLiveTable({ rows }: { rows: LiveTimingRowDto[] }) {
  return (
    <table className="w-full text-2xl lg:text-4xl" aria-label="Live timing">
      <thead>
        <tr className="border-b border-slate-700 text-left text-lg text-slate-400 lg:text-2xl">
          <th className="py-2 pr-4">Pos</th>
          <th className="py-2 pr-4">Car</th>
          <th className="py-2 pr-4">Driver</th>
          <th className="py-2 pr-4 text-right">Laps</th>
          <th className="py-2 pr-4 text-right">Total</th>
          <th className="py-2 pr-4 text-right">Last</th>
          <th className="py-2 pr-4 text-right">Best</th>
          <th className="py-2 text-right">Gap</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.entryId} className="border-b border-slate-800">
            <td className="py-3 pr-4 font-bold tabular-nums">{row.position}</td>
            <td className="py-3 pr-4 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="py-3 pr-4 font-semibold">{row.driverName}{row.finished && <FinishedFlag />}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{row.lapsCompleted}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.totalTimeMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.lastLapMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.bestLapMs)}</td>
            <td className="py-3 text-right tabular-nums text-slate-300">{gap(row)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
