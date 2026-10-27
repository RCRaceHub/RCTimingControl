import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { fmtMs, gapLabel } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar, type LapBarClock } from '@/components/LapProgressBar';
import { PredictedLaps } from '@/components/PredictedLaps';
import { PositionArrow } from '@/components/PositionArrow';
import { isClose } from '@/lib/lapProgress';
import { CarThumb } from '@/components/CarThumb';
import type { RaceMediaDto } from '@/lib/boardsApi';
import { cn } from '@/lib/utils';
import { gapText } from './boardFormat';

interface BoardLiveTableProps {
  rows: LiveTimingRowDto[];
  /** How the lap bars read the clock; omitted to leave the bars out. */
  barClock?: LapBarClock;
  /** A car this close behind the one ahead, in milliseconds, is marked; 0 or less turns the mark off. */
  closeMs?: number;
  /** Show the places gained or lost against the grid beside each position; for finals. */
  showArrows?: boolean;
  /** Given for a timed race, adds a column of the laps each car is on course to finish with. */
  predicted?: PredictedClock;
  /** Car photos by entry id, shown beside the driver's name (#521); omitted or empty leaves the rows as they were. */
  media?: Map<number, RaceMediaDto>;
}

/** What the predicted laps column needs: the device clock offset and the race time left at a device time. */
export interface PredictedClock {
  clock: LapBarClock;
  remainingAt: (deviceNowMs: number) => number | null;
}

/**
 * The live timing table of a board: the order with each car's laps, last and best lap, the gap to the car ahead
 * and to the leader, a marker on cars running close, and (given {@code barClock}) a bar for how far each car is
 * round its lap against its average. Given {@code predicted} it adds the laps each car is on course to finish
 * with; {@code showArrows} adds an arrow for places gained or lost since the grid, for finals (#479).
 */
export function BoardLiveTable({ rows, barClock, closeMs = 0, showArrows = false, predicted, media }: BoardLiveTableProps) {
  return (
    <table className="w-full text-2xl lg:text-4xl" aria-label="Live timing">
      <thead>
        <tr className="border-b border-slate-700 text-left text-lg text-slate-400 lg:text-2xl">
          <th className="py-2 pr-4">Pos</th>
          <th className="py-2 pr-4">Car</th>
          <th className="py-2 pr-4">Driver</th>
          <th className="py-2 pr-4 text-right">Laps</th>
          {predicted && <th className="py-2 pr-4 text-right">Pred</th>}
          <th className="py-2 pr-4 text-right">Total</th>
          <th className="py-2 pr-4 text-right">Last</th>
          <th className="py-2 pr-4 text-right">Best</th>
          <th className="py-2 pr-4 text-right">Ahead</th>
          <th className="py-2 text-right">Gap</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => {
          const close = isClose(row, closeMs);
          return (
          <tr
            key={row.entryId}
            className={cn('border-b border-slate-800', close && 'bg-amber-500/20')}
            data-close={close || undefined}
          >
            <td className="py-3 pr-4 font-bold tabular-nums">{row.position}{showArrows && <PositionArrow row={row} rows={rows} />}</td>
            <td className="py-3 pr-4 tabular-nums">{row.carNumber ?? '—'}</td>
            <td className="py-3 pr-4 font-semibold">
              <CarThumb url={media?.get(row.entryId)?.carPhotoUrl} className="mr-3 h-10 w-16" />
              {row.driverName}{row.finished && <FinishedFlag />}
              {barClock && <LapProgressBar row={row} clock={barClock} className="mt-1" />}
            </td>
            <td className="py-3 pr-4 text-right tabular-nums">{row.lapsCompleted}</td>
            {predicted && (
              <td className="py-3 pr-4 text-right tabular-nums text-slate-300">
                <PredictedLaps row={row} clock={predicted.clock} remainingAt={predicted.remainingAt} />
              </td>
            )}
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.totalTimeMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.lastLapMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums">{fmtMs(row.bestLapMs)}</td>
            <td className="py-3 pr-4 text-right tabular-nums text-slate-300">{gapText(row)}</td>
            <td className="py-3 text-right tabular-nums text-slate-300">{gapLabel(row.position, row.lapsDown, row.gapToLeaderMs)}</td>
          </tr>
          );
        })}
      </tbody>
    </table>
  );
}
