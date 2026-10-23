import type { EventResultsRace } from '@/lib/raceControlApi';
import { averageLapMs, lapTimesFor } from '@/lib/heatSheet';
import { startPosition } from '@/lib/eventPrint';
import { fmtMs } from '@/lib/format';

/**
 * One heat's result as it prints: position, driver, car, the grid position the driver started from, laps, total
 * time, best and average lap and the gap. A Points column follows when the round is scored by points, and a
 * Champ. pts column for a final that earns championship points. Names are
 * the real ones; this is for officials' paper only.
 */
export function HeatResultTable({ race }: { race: EventResultsRace }) {
  const { positions, lapHistory } = race.result;
  const hasPoints = positions.some((r) => r.points != null);
  const hasChampionshipPoints = positions.some((r) => r.championshipPoints != null);
  return (
    <table className="w-full border-collapse text-sm">
      <thead>
        <tr className="border-b-2 border-foreground">
          <th scope="col" className="w-10 py-2 pr-4 text-left">Pos</th>
          <th scope="col" className="py-2 pr-4 text-left">Driver</th>
          <th scope="col" className="py-2 pr-4 text-left">Car</th>
          <th scope="col" className="py-2 pr-4 text-right">Start</th>
          <th scope="col" className="py-2 pr-4 text-right">Laps</th>
          <th scope="col" className="py-2 pr-4 text-right">Total Time</th>
          <th scope="col" className="py-2 pr-4 text-right">Best Lap</th>
          <th scope="col" className="py-2 pr-4 text-right">Avg Lap</th>
          <th scope="col" className="py-2 text-right">Gap</th>
          {hasPoints && <th scope="col" className="py-2 pl-4 text-right">Points</th>}
          {hasChampionshipPoints && <th scope="col" className="py-2 pl-4 text-right">Champ. pts</th>}
        </tr>
      </thead>
      <tbody>
        {positions.map((row) => (
          <tr key={row.entryId} className="border-b border-border/50 print:break-inside-avoid">
            <td className="py-1.5 pr-4 font-semibold">{row.position}</td>
            <td className="py-1.5 pr-4">{row.driverName}</td>
            <td className="py-1.5 pr-4 text-muted-foreground">{row.carNumber ?? '—'}</td>
            <td className="py-1.5 pr-4 text-right font-mono">{startPosition(race, row.entryId) ?? '—'}</td>
            <td className="py-1.5 pr-4 text-right font-mono">{row.lapsCompleted}</td>
            <td className="py-1.5 pr-4 text-right font-mono">{fmtMs(row.totalTimeMs)}</td>
            <td className="py-1.5 pr-4 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
            <td className="py-1.5 pr-4 text-right font-mono">
              {fmtMs(averageLapMs(lapTimesFor(row.entryId, lapHistory)))}
            </td>
            <td className="py-1.5 text-right font-mono text-muted-foreground">
              {row.position === 1 ? '—' : fmtMs(row.gapToLeaderMs)}
            </td>
            {hasPoints && <td className="py-1.5 pl-4 text-right font-mono">{row.points ?? '—'}</td>}
            {hasChampionshipPoints && (
              <td className="py-1.5 pl-4 text-right font-mono">{row.championshipPoints ?? '—'}</td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
