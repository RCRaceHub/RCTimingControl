// The overlay's running order drawn as a timing tower, like a Formula 1 broadcast: one row per car with its
// place, car number, driver and gap in type big enough to read a name on a stream, and rows that slide to their new place when the order changes.
import { useEffect, useRef } from 'react';
import { cn } from '@/lib/utils';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar, type LapBarClock } from '@/components/LapProgressBar';
import { PaceIcon } from '@/components/PaceIcon';
import { PredictedLaps } from '@/components/PredictedLaps';
import { PositionArrow } from '@/components/PositionArrow';
import { RowMarkers } from '@/components/RowBadges';
import { useNoSignal } from '@/hooks/race-control/useNoSignal';
import { RowStateIcon } from '@/components/StateIcon';
import type { FlashColor } from '@/hooks/race-control/useLapFlash';
import { isClose } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import type { PredictedClock } from './BoardLiveTable';
import { gapText, leaderGapText } from './boardFormat';

/** The height of one row in rem; the rows are placed by it, so it is the one number the layout hangs on. */
export const TOWER_ROW_REM = 2.75;
/** The panel width of the tower: a name wider than its room scrolls to fit beside the laps, predicted laps, qualifying place and gap. */
export const TOWER_WIDTH_CLASS = 'w-[24rem]';

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
  lapFlash: Map<number, FlashColor | undefined>;
  closeMs: number;
  showArrows: boolean;
  /** Places at the front of a final that move up into the final above (#530); a line is drawn beneath the last. */
  bumpUpPlaces?: number;
  /** Tag each row with its class colour when the rows span more than one class (#532); never drawn for one class. */
  classTags?: boolean;
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
  /** Which the shared laps column shows: laps done, or (for a timed race) the laps on course to finish with. */
  lapsView?: 'laps' | 'predicted';
  /** The laps column is fading out ahead of a change. */
  lapsFading?: boolean;
}

const FLASH_CLASS: Record<string, string> = {
  'race-best': 'bg-purple-500/40',
  'personal-best': 'bg-blue-500/40',
  improving: 'bg-green-500/40',
  slow: 'bg-red-500/40',
};

/** A driver name that scrolls to show its end when it is wider than the room it has, and sits still otherwise. */
function ScrollingName({ name }: { name: string }) {
  const outer = useRef<HTMLSpanElement>(null);
  const inner = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const box = outer.current;
    const text = inner.current;
    if (!box || !text) return;
    const measure = () => {
      const overflow = text.scrollWidth - box.clientWidth;
      if (overflow > 0) {
        text.style.setProperty('--name-overflow', `${overflow}px`);
        text.dataset.overflow = 'true';
      } else {
        delete text.dataset.overflow;
      }
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(box);
    return () => observer.disconnect();
  }, [name]);
  return (
    <span ref={outer} className="block overflow-hidden whitespace-nowrap">
      <span ref={inner} className="name-scroll inline-block">
        {name}
      </span>
    </span>
  );
}

function QualifyingChip({ projected, final }: { projected: ProjectedQualifying; final: boolean }) {
  const gained = projected.previousPosition === null ? null : projected.previousPosition - projected.position;
  return (
    <span className="whitespace-nowrap text-sm tabular-nums" title={final ? 'Qualifying place after this heat' : 'Projected qualifying place if the heat ended now'}>
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
  bumpUpPlaces = 0,
  classTags = false,
  gap = 'int',
  gapFading = false,
  lapsView = 'laps',
  lapsFading = false,
}: OverlayTowerProps) {
  const noSignal = useNoSignal(allRows, barClock);
  const tagging = classTags && new Set(allRows.map((r) => r.classColour ?? null)).size > 1;
  const showQualifying = qualifying !== undefined && qualifying.size > 0;
  const showPredicted = predicted !== undefined && lapsView === 'predicted';
  const lapsClass = cn('transition-opacity duration-200', lapsFading && 'opacity-0');
  const gapClass = cn('transition-opacity duration-200', gapFading && 'opacity-0');
  return (
    <>
      <div
        aria-hidden
        className={cn('flex items-center gap-1.5 px-2 pt-1 text-xs font-semibold tracking-widest', colours.muted)}
      >
        <span className="w-8 shrink-0" />
        <span className="w-7 shrink-0 text-right">#</span>
        <span className="min-w-0 flex-1">DRIVER</span>
        <span className={cn('w-9 shrink-0 text-right', lapsClass)} title={showPredicted ? 'Laps on course to finish with' : 'Laps completed'}>
          {showPredicted ? 'PRED' : 'LAP'}
        </span>
        {showQualifying && <span className="w-12 shrink-0 text-right" title="Qualifying place">QUAL</span>}
        <span className={cn('w-[4.5rem] shrink-0 text-right', gapClass)}>{gap === 'int' ? 'INT' : 'GAP'}</span>
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
          const bumpLine = bumpUpPlaces > 0 && index === bumpUpPlaces;
          return (
            <div
              key={row.entryId}
              role="row"
              data-close={close || undefined}
              data-bump-line={bumpLine || undefined}
              className={cn(
                'absolute inset-x-0 flex items-center gap-1.5 border-t px-2 transition-[transform,background-color] duration-700 ease-in-out',
                colours.row,
                bumpLine && 'border-t-2 border-t-emerald-400',
                noSignal.has(row.entryId) && 'opacity-50',
                close && !flash && 'bg-amber-500/25',
                flash && FLASH_CLASS[flash],
              )}
              style={{ height: `${TOWER_ROW_REM}rem`, transform: `translateY(${index * TOWER_ROW_REM}rem)` }}
            >
              {tagging && (
                <span
                  aria-hidden
                  data-class-tag
                  className="absolute inset-y-0.5 left-0 w-1 rounded-r"
                  style={{ backgroundColor: row.classColour ?? 'transparent' }}
                />
              )}
              <span
                className={cn(
                  'inline-flex h-8 w-8 shrink-0 text-lg items-center justify-center rounded font-bold tabular-nums',
                  colours.position,
                )}
              >
                {row.position}
              </span>
              <span className={cn('w-7 shrink-0 text-right text-lg tabular-nums', colours.muted)}>{row.carNumber ?? ''}</span>
              <span className="relative flex min-w-0 flex-1 items-center text-lg font-semibold">
                <ScrollingName name={row.driverName} />
                {/* The slot is always there, so the name box keeps its width and the scroll does not restart on each flash */}
                <span className="ml-1 inline-block h-[1em] w-[1em] shrink-0 text-base [[data-contrast=high]_&]:w-auto [[data-contrast=high]_&]:min-w-[1em]">
                  <RowStateIcon flash={flash} close={close} />
                </span>
                {/* Its own slot, like the flash icon, so the name keeps its width when the thumbs up or down comes and goes */}
                {barClock && (
                  <span className="ml-1 inline-block h-[1em] w-[1em] shrink-0 text-base [[data-contrast=high]_&]:w-auto [[data-contrast=high]_&]:min-w-[1em]">
                    {!row.finished && <PaceIcon row={row} clock={barClock} />}
                  </span>
                )}
                {showArrows && row.startPosition != null && <PositionArrow row={row} rows={allRows} />}
                <RowMarkers row={row} noSignal={noSignal.has(row.entryId)} className="ml-1" />
                {row.finished && <FinishedFlag className="ml-1" />}
                {barClock && !row.finished && (
                  <LapProgressBar row={row} clock={barClock} className="absolute inset-x-0 -bottom-1 h-0.5" />
                )}
              </span>
              <span className={cn('w-9 shrink-0 text-right text-lg tabular-nums', colours.muted, lapsClass)}>
                {showPredicted ? (
                  <PredictedLaps row={row} clock={predicted.clock} remainingAt={predicted.remainingAt} />
                ) : (
                  row.lapsCompleted
                )}
              </span>
              {showQualifying && (
                <span className="w-12 shrink-0 text-right">
                  {projected && <QualifyingChip projected={projected} final={qualifyingFinal} />}
                </span>
              )}
              <span className={cn('w-[4.5rem] shrink-0 text-right text-lg font-semibold tabular-nums', gapClass)}>
                {gap === 'int' ? gapText(row) : leaderGapText(row)}
              </span>
            </div>
          );
        })}
      </div>
    </>
  );
}
