import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { fmtMs } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar } from '@/components/LapProgressBar';
import { isClose } from '@/lib/lapProgress';

function gap(row: LiveTimingRowDto): string {
  if (row.position === 1) return '—';
  if (row.lapsDown > 0) return `+${row.lapsDown} ${row.lapsDown === 1 ? 'lap' : 'laps'}`;
  return row.gapToLeaderMs !== null ? `+${fmtMs(row.gapToLeaderMs)}` : '—';
}

function interval(row: LiveTimingRowDto): string {
  if (row.position === 1) return '—';
  if (row.intervalLapsDown > 0) return `+${row.intervalLapsDown} ${row.intervalLapsDown === 1 ? 'lap' : 'laps'}`;
  return row.gapToAheadMs !== null ? `+${fmtMs(row.gapToAheadMs)}` : '—';
}

interface BoardLiveTableProps {
  rows: LiveTimingRowDto[];
  /** The server's clock in UTC milliseconds, for the lap bars; omitted to leave the bars out. */
  nowMs?: number;
  /** A car this close behind the one ahead, in milliseconds, is marked; 0 or less turns the mark off. */
  closeMs?: number;
}

/**
 * The live timing table of a board: the order with each car's laps, last and best lap, the gap to the car ahead
 * and to the leader, a marker on cars running close, and (given {@code nowMs}) a bar for how far each car is
 * round its lap against its average (#479).
 */
export function BoardLiveTable({ rows, nowMs, closeMs = 0 }: BoardLiveTableProps) {
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
          <th className="py-2 pr-4 text-right">Int</th>
          <th className="py-2 text-right">Gap</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr
            key={row.entryId}
            className={`border-b border-slate-800 ${isClose(row, closeMs) ? 'bg-amber-500/20' : ''}`}
            data-close={isClose(row, closeMs) || undefined}
          >
            <td className="py-3 pr-4 font-bold tabular-nums">{row.position}</td>
            <td className="py-3 pr-4 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="py-3 pr-4 font-semibold">
              {row.driverName}{row.finished && <FinishedFlag />}
              {nowMs !== undefined && <LapProgressBar row={row} nowMs={nowMs} className="mt-1" />}
            </td>
            <td className="py-3 pr-4 text-right tabular-nums">{row.lapsCompleted}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.totalTimeMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.lastLapMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.bestLapMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums text-slate-300">{interval(row)}</td>
            <td className="py-3 text-right tabular-nums text-slate-300">{gap(row)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
