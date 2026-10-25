// The overlay's running order drawn as a timing tower, like a Formula 1 broadcast: one slim row per car with its
// place, car number, driver and gap, and rows that slide to their new place when the order changes.
import { cn } from '@/lib/utils';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar, type LapBarClock } from '@/components/LapProgressBar';
import { PredictedLaps } from '@/components/PredictedLaps';
import { PositionArrow } from '@/components/PositionArrow';
import { isClose } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import type { PredictedClock } from './BoardLiveTable';
import { gapText, leaderGapText } from './boardFormat';

/** The height of one row in rem; the rows are placed by it, so it is the one number the layout hangs on. */
export const TOWER_ROW_REM = 2;
/** The panel width of the tower: the name truncates to fit beside the predicted laps, qualifying place and gap. */
export const TOWER_WIDTH_CLASS = 'w-[20rem]';

/** A driver's projected place in the event's qualifying table, if the heat ended now. */
export interface ProjectedQualifying {
  position: number;
  previousPosition: number | null;
}

/** The colours of the overlay theme the tower is drawn in. */
export interface TowerColours {
  muted: string;
  row: string;
  position: string;
}

interface OverlayTowerProps {
  /** The cars to show, in order. */
  rows: LiveTimingRowDto[];
  /** The whole field, which the places-gained arrows are judged against. */
  allRows: LiveTimingRowDto[];
  colours: TowerColours;
  /** The flash colour class of each car's latest lap, by entry id. */
  lapFlash: Map<number, string | undefined>;
  closeMs: number;
  showArrows: boolean;
  /** How the lap bars read the clock; omitted to leave the bars out. */
  barClock?: LapBarClock;
  /** Given for a timed race, adds the laps each car is on course to finish with. */
  predicted?: PredictedClock;
  /** Given for a qualifying heat, adds each driver's projected qualifying place by entry id. */
  qualifying?: Map<number, ProjectedQualifying>;
  /** The heat has ended, so the places in {@code qualifying} are settled rather than a projection. */
  qualifyingFinal?: boolean;
  /** The gap on view: the interval to the car ahead (INT) or the gap to the leader (GAP); INT unless given. */
  gap?: 'int' | 'leader';
  /** The gap column is fading out ahead of a change in auto mode. */
  gapFading?: boolean;
}

const FLASH_CLASS: Record<string, string> = {
  'race-best': 'bg-purple-500/40',
  'personal-best': 'bg-blue-500/40',
  improving: 'bg-green-500/40',
  slow: 'bg-red-500/40',
};

function QualifyingChip({ projected, final }: { projected: ProjectedQualifying; final: boolean }) {
  const gained = projected.previousPosition === null ? null : projected.previousPosition - projected.position;
  return (
    <span className="whitespace-nowrap text-xs tabular-nums" title={final ? 'Qualifying place after this heat' : 'Projected qualifying place if the heat ended now'}>
      Q{projected.position}
      {gained !== null && gained !== 0 && (
        <span className={gained > 0 ? 'text-emerald-500' : 'text-rose-500'}>
          {gained > 0 ? '▲' : '▼'}
          {Math.abs(gained)}
        </span>
      )}
    </span>
  );
}

/**
 * The timing tower. Each row is placed with a transform from its index, so a change of order slides the rows
 * rather than redrawing them. The rows are role "row" elements in a labelled "Running order" list.
 */
export function OverlayTower({
  rows,
  allRows,
  colours,
  lapFlash,
  closeMs,
  showArrows,
  barClock,
  predicted,
  qualifying,
  qualifyingFinal = false,
  gap = 'int',
  gapFading = false,
}: OverlayTowerProps) {
  const gapClass = cn('transition-opacity duration-200', gapFading && 'opacity-0');
  return (
    <>
      <div aria-hidden className={cn('flex justify-end px-2 pt-1 text-[10px] font-semibold tracking-widest', colours.muted)}>
        <span className={cn('w-16 text-right', gapClass)}>{gap === 'int' ? 'INT' : 'GAP'}</span>
      </div>
      <div
        role="table"
        aria-label="Running order"
        className="relative"
        style={{ height: `${rows.length * TOWER_ROW_REM}rem` }}
      >
        {rows.map((row, index) => {
          const flash = lapFlash.get(row.entryId);
          const close = isClose(row, closeMs);
          const projected = qualifying?.get(row.entryId);
          return (
            <div
              key={row.entryId}
              role="row"
              data-close={close || undefined}
              className={cn(
                'absolute inset-x-0 flex items-center gap-1.5 border-t px-2 transition-[transform,background-color] duration-700 ease-in-out',
                colours.row,
                close && !flash && 'bg-amber-500/25',
                flash && FLASH_CLASS[flash],
              )}
              style={{ height: `${TOWER_ROW_REM}rem`, transform: `translateY(${index * TOWER_ROW_REM}rem)` }}
            >
              <span
                className={cn(
                  'inline-flex h-6 w-6 shrink-0 text-sm items-center justify-center rounded font-bold tabular-nums',
                  colours.position,
                )}
              >
                {row.position}
              </span>
              <span className={cn('w-6 shrink-0 text-right text-sm tabular-nums', colours.muted)}>{row.carNumber ?? ''}</span>
              <span className="relative min-w-0 flex-1 truncate font-semibold">
                {row.driverName}
                {showArrows && row.startPosition != null && <PositionArrow row={row} rows={allRows} />}
                {row.finished && <FinishedFlag className="ml-1" />}
                {barClock && !row.finished && (
                  <LapProgressBar row={row} clock={barClock} className="absolute inset-x-0 -bottom-1 h-0.5" />
                )}
              </span>
              {predicted && (
                <span className={cn('w-6 shrink-0 text-right text-sm tabular-nums', colours.muted)} title="Laps on course to finish with">
                  <PredictedLaps row={row} clock={predicted.clock} remainingAt={predicted.remainingAt} />
                </span>
              )}
              {projected && <QualifyingChip projected={projected} final={qualifyingFinal} />}
              <span className={cn('w-16 shrink-0 text-right text-sm font-semibold tabular-nums', gapClass)}>
                {gap === 'int' ? gapText(row) : leaderGapText(row)}
              </span>
            </div>
          );
        })}
      </div>
    </>
  );
}
