import type { FinalLegs } from '@/lib/boardsApi';

/**
 * The combined result of an A final run in legs: each driver's points in every leg (a dropped leg is struck
 * through) and the total, lowest first. Shown under the last leg's own result on the results board.
 */
export function BoardLegsTable({ legs }: { legs: FinalLegs }) {
  const heading = legs.complete
    ? `${legs.finalLetter} Final overall, best ${legs.legsCounted || legs.legs} of ${legs.legs} legs`
    : `${legs.finalLetter} Final after ${legs.legsFinished} of ${legs.legs} legs`;
  const legNumbers = Array.from({ length: legs.rows[0]?.legPoints.length ?? 0 }, (_, i) => i + 1);
  return (
    <section className="mt-10" aria-label="A final overall">
      <h2 className="mb-3 text-3xl font-bold lg:text-4xl">{heading}</h2>
      <table className="w-full text-2xl lg:text-4xl" aria-label="Overall">
        <thead>
          <tr className="border-b border-slate-700 text-left text-lg text-slate-400 lg:text-2xl">
            <th className="py-2 pr-4">Pos</th>
            <th className="py-2 pr-4">Driver</th>
            {legNumbers.map((n) => (
              <th key={n} className="py-2 pr-4 text-right">Leg {n}</th>
            ))}
            <th className="py-2 text-right">Points</th>
          </tr>
        </thead>
        <tbody>
          {legs.rows.map((row) => (
            <tr key={row.entryId} className="border-b border-slate-800">
              <td className="py-3 pr-4 font-bold tabular-nums">{row.position}</td>
              <td className="py-3 pr-4 font-semibold">{row.driverName}</td>
              {row.legPoints.map((points, i) => (
                <td
                  key={i}
                  className={`py-3 pr-4 text-right tabular-nums ${row.counted[i] ? '' : 'text-slate-500 line-through'}`}
                >
                  {points}
                </td>
              ))}
              <td className="py-3 text-right font-bold tabular-nums">{row.points}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
