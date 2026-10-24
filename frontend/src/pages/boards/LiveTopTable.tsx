// The qualifying order of the live top board, drawn once for the board itself and for a saved view's panel (#480, #481).
import { type LiveQualifyingDto, type LiveQualifyingRow } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';

/** How a driver's place changed: positive is up the table, null when they had no earlier place. */
function placesGained(row: LiveQualifyingRow): number | null {
  return row.previousPosition === null ? null : row.previousPosition - row.position;
}

function Movement({ row, hasBaseline }: { row: LiveQualifyingRow; hasBaseline: boolean }) {
  const gained = placesGained(row);
  // The first heat of the event has nothing to compare with, so nobody is "new"
  if (gained === null) return !hasBaseline ? null : <span className="text-sky-300">New</span>;
  if (gained > 0) return <span className="text-emerald-400">▲ {gained}</span>;
  if (gained < 0) return <span className="text-rose-400">▼ {-gained}</span>;
  return <span className="text-slate-600">–</span>;
}

function rowTint(row: LiveQualifyingRow, live: boolean, hasBaseline: boolean): string {
  // The heat on track in amber while it runs; once it ends, whoever it moved stays lit green or red
  if (live && row.live) return 'bg-amber-500/20';
  if (!live && row.previousPosition !== null && row.previousPosition !== row.position) {
    return row.position < row.previousPosition ? 'bg-emerald-500/15' : 'bg-rose-500/15';
  }
  if (!live && hasBaseline && row.previousPosition === null) return 'bg-sky-500/15';
  return '';
}

/** The table of the first {@code top} places, tinted and with movement arrows as the phase of the heat asks. */
export function LiveTopTable({ data, top }: { data: LiveQualifyingDto; top: number }) {
  const live = data.phase === 'LIVE';
  const rows = data.rows.slice(0, top);
  const hasBaseline = data.rows.some((r) => r.previousPosition !== null);
  return (
    <table className="w-full text-2xl lg:text-4xl" aria-label="Qualifying order">
      <thead>
        <tr className="border-b border-slate-700 text-left text-lg text-slate-400 lg:text-2xl">
          <th className="py-2 pr-4">Pos</th>
          <th className="py-2 pr-4">Driver</th>
          <th className="py-2 pr-4">Car</th>
          <th className="py-2 pr-4 text-right">Points</th>
          <th className="py-2 pr-4 text-right">Best lap</th>
          <th className="py-2 text-right">Moved</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.entryId} className={`border-b border-slate-800 ${rowTint(row, live, hasBaseline)}`}>
            <td className="py-3 pr-4 font-bold tabular-nums">{row.position}</td>
            <td className="py-3 pr-4 font-semibold">
              {row.driverName}
              {live && row.live && <span className="ml-3 text-lg text-amber-300">on track</span>}
              {live && row.waiting && <span className="ml-3 text-lg text-slate-500">heat to come</span>}
            </td>
            <td className="py-3 pr-4 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="py-3 pr-4 text-right font-semibold tabular-nums">{row.points ?? '—'}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.bestLapMs)}</td>
            <td className="py-3 text-right tabular-nums">
              <Movement row={row} hasBaseline={hasBaseline} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
