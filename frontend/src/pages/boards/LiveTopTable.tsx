// The qualifying tables of the live top board, drawn once for the board itself and for a saved view's panel (#480,
// #481): the order inside the round on track and the overall order, side by side.
import { type LiveQualifyingDto, type LiveQualifyingRow, type LiveRoundRow } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import { TimingLegend } from '@/components/TimingLegend';

/** How a driver's place changed: positive is up the table, null when they had no earlier place. */
function placesGained(row: LiveQualifyingRow | LiveRoundRow): number | null {
  return row.previousPosition === null ? null : row.previousPosition - row.position;
}

function Movement({ row, hasBaseline }: { row: LiveQualifyingRow | LiveRoundRow; hasBaseline: boolean }) {
  const gained = placesGained(row);
  // The first heat of the event has nothing to compare with, so nobody is "new"
  if (gained === null) return !hasBaseline ? null : <span className="text-sky-300">New</span>;
  if (gained > 0) return <span className="text-emerald-400">▲ {gained}</span>;
  if (gained < 0) return <span className="text-rose-400">▼ {-gained}</span>;
  return <span className="text-slate-600">–</span>;
}

function rowTint(row: LiveQualifyingRow | LiveRoundRow, live: boolean, hasBaseline: boolean): string {
  // The heat on track in amber while it runs; once it ends, whoever it moved stays lit green or red
  if (live && row.live) return 'bg-amber-500/20';
  if (!live && row.previousPosition !== null && row.previousPosition !== row.position) {
    return row.position < row.previousPosition ? 'bg-emerald-500/15' : 'bg-rose-500/15';
  }
  if (!live && hasBaseline && row.previousPosition === null) return 'bg-sky-500/15';
  return '';
}

type Density = { text: string; head: string; cell: string };

const LARGE: Density = { text: 'text-2xl lg:text-4xl', head: 'text-lg lg:text-2xl', cell: 'py-3 pr-4' };
/** Two tables share the screen, so each is set smaller. */
const COMPACT: Density = { text: 'text-lg lg:text-2xl', head: 'text-sm lg:text-lg', cell: 'py-1.5 pr-3' };

/** The overall table of the first {@code top} places, tinted and with movement arrows as the phase of the heat asks. */
function OverallTable({ data, top, density }: { data: LiveQualifyingDto; top: number; density: Density }) {
  const live = data.phase === 'LIVE';
  const rows = data.rows.slice(0, top);
  const hasBaseline = data.rows.some((r) => r.previousPosition !== null);
  const practice = data.roundType === 'PRACTICE';
  // A final of one leg has no points to show, so its table gives laps instead
  const showLaps = practice || data.rows.every((r) => r.points === null);
  const label = practice ? 'Practice order' : data.roundType === 'FINAL' ? 'Final order' : 'Qualifying order';
  return (
    <table className={`w-full ${density.text}`} aria-label={label}>
      <thead>
        <tr className={`border-b border-slate-700 text-left text-slate-400 ${density.head}`}>
          <th className={density.cell}>Pos</th>
          <th className={density.cell}>Driver</th>
          <th className={density.cell}>Car</th>
          {showLaps ? (
            <th className={`${density.cell} text-right`}>Laps</th>
          ) : (
            <th className={`${density.cell} text-right`}>Points</th>
          )}
          <th className={`${density.cell} text-right`}>{practice ? (data.rankingLabel ?? 'Best') : 'Best lap'}</th>
          <th className="py-2 text-right">Moved</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.entryId} className={`border-b border-slate-800 ${rowTint(row, live, hasBaseline)}`}>
            <td className={`${density.cell} font-bold tabular-nums`}>{row.position}</td>
            <td className={`${density.cell} font-semibold`}>
              {row.driverName}
              {live && row.live && <span className="ml-3 text-amber-300">on track</span>}
              {live && row.waiting && <span className="ml-3 text-slate-500">heat to come</span>}
            </td>
            <td className={`${density.cell} tabular-nums`}>{row.carNumber ?? '—'}</td>
            {showLaps ? (
              <td className={`${density.cell} text-right tabular-nums`}>{row.lapsCompleted}</td>
            ) : (
              <td className={`${density.cell} text-right font-semibold tabular-nums`}>{row.points ?? '—'}</td>
            )}
            <td className={`${density.cell} text-right tabular-nums ${practice ? 'font-semibold' : ''}`}>
              {fmtMs(row.bestLapMs)}
            </td>
            <td className="text-right tabular-nums">
              <Movement row={row} hasBaseline={hasBaseline} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * The order inside the round on track: laps, then total time, over the round's heats counted so far. In practice the
 * round is the session, ranked by the event's practice time instead.
 */
function RoundTable({ data, rows, top, density }: {
  data: LiveQualifyingDto;
  rows: LiveRoundRow[];
  top: number;
  density: Density;
}) {
  const live = data.phase === 'LIVE';
  // A driver whose heat is later in the round has no earlier place in it, so nobody is marked "New" here
  const hasBaseline = false;
  const practice = data.roundType === 'PRACTICE';
  return (
    <table className={`w-full ${density.text}`} aria-label="Round order">
      <thead>
        <tr className={`border-b border-slate-700 text-left text-slate-400 ${density.head}`}>
          <th className={density.cell}>Pos</th>
          <th className={density.cell}>Driver</th>
          <th className={density.cell}>Car</th>
          <th className={`${density.cell} text-right`}>Laps</th>
          {!practice && <th className={`${density.cell} text-right`}>Time</th>}
          <th className={`${density.cell} text-right`}>{practice ? (data.rankingLabel ?? 'Best') : 'Best lap'}</th>
          <th className="py-2 text-right">Moved</th>
        </tr>
      </thead>
      <tbody>
        {rows.slice(0, top).map((row) => (
          <tr key={row.entryId} className={`border-b border-slate-800 ${rowTint(row, live, hasBaseline)}`}>
            <td className={`${density.cell} font-bold tabular-nums`}>{row.position}</td>
            <td className={`${density.cell} font-semibold`}>
              {row.driverName}
              {live && row.live && <span className="ml-3 text-amber-300">on track</span>}
            </td>
            <td className={`${density.cell} tabular-nums`}>{row.carNumber ?? '—'}</td>
            <td className={`${density.cell} text-right font-semibold tabular-nums`}>{row.lapsCompleted}</td>
            {!practice && <td className={`${density.cell} text-right tabular-nums`}>{fmtMs(row.totalTimeMs)}</td>}
            <td className={`${density.cell} text-right tabular-nums ${practice ? 'font-semibold' : ''}`}>
              {fmtMs(row.bestLapMs)}
            </td>
            <td className="text-right tabular-nums">
              <Movement row={row} hasBaseline={hasBaseline} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * The live tables of the first {@code top} places. When the class has a round to show, the round's order and the
 * overall order sit side by side, each under its own heading; otherwise the overall order fills the board. With
 * {@code showLegend} a key to the movement colours sits under the tables.
 */
export function LiveTopTable({ data, top, showLegend = false }: { data: LiveQualifyingDto; top: number; showLegend?: boolean }) {
  const roundRows = data.roundRows ?? [];
  const legend = showLegend && <TimingLegend kinds={['movement']} size="board" className="mt-3 text-slate-400" />;
  if (roundRows.length === 0) {
    return (
      <>
        <OverallTable data={data} top={top} density={top > 10 ? COMPACT : LARGE} />
        {legend}
      </>
    );
  }
  return (
    <>
      <div className="grid gap-8 xl:grid-cols-2">
        <section aria-label="Round">
          <h2 className="mb-2 text-2xl font-bold text-slate-200 lg:text-3xl">
            {data.roundTitle
              ? data.roundTitle
              : data.roundType === 'PRACTICE'
                ? `Practice ${data.roundNumber ?? ''}`.trim()
                : data.roundNumber
                  ? `Round ${data.roundNumber}`
                  : 'This round'}
          </h2>
          <RoundTable data={data} rows={roundRows} top={top} density={COMPACT} />
        </section>
        <section aria-label="Overall">
          <h2 className="mb-2 text-2xl font-bold text-slate-200 lg:text-3xl">{data.overallTitle ?? 'Overall'}</h2>
          <OverallTable data={data} top={top} density={COMPACT} />
        </section>
      </div>
      {legend}
    </>
  );
}
