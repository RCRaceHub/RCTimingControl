import { RowIconsProvider } from '@/components/RowIconsContext';
import { OperatorTimingControls } from '@/components/OperatorTimingControls';
import { useOperatorRowIcons } from '@/hooks/race-control/useOperatorRowIcons';
import { useMemo } from 'react';
import type { ReactNode } from 'react';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { useLappedBadge } from '@/hooks/race-control/useLappedBadge';
import { useLapFlash } from '@/hooks/race-control/useLapFlash';
import { useEliminations } from '@/hooks/race-control/useEliminations';
import type { RunOrderItemDto } from '@/lib/raceControlApi';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { EliminationButtons } from './EliminationButtons';
import { cn } from '@/lib/utils';
import { fmtMs, gapLabel } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar } from '@/components/LapProgressBar';
import { PaceIcon } from '@/components/PaceIcon';
import { FlyingLapIcon } from '@/components/RowBadges';
import { PositionArrow } from '@/components/PositionArrow';
import { RowStateIcon } from '@/components/StateIcon';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import { holdBackLapped, isClose } from '@/lib/lapProgress';
import { CarThumb } from '@/components/CarThumb';
import { carColoursFor } from '@/lib/carColours';
import { useRaceMedia } from '@/hooks/useRaceMedia';
import { useBoardSettings } from '@/hooks/useBoardSettings';
import { useSectors } from '@/hooks/race-control/useSectors';
import { sectorTone } from '@/lib/sectors';

type Props = {
  raceId: number;
  status: RunOrderItemDto['status'];
  /** Entry IDs to highlight (e.g. proximity alerts from referee view). */
  highlightEntryIds?: Set<number>;
  /** Entry IDs that jumped a stagger start, flagged on their row. */
  jumpStartEntryIds?: Set<number>;
  /** Show the places gained or lost against the grid beside each position; for finals. */
  showArrows?: boolean;
  /** Extra controls for each row, shown in a last column; the referee screen's Incident and Penalty buttons (#771). */
  rowActions?: (row: ReturnType<typeof useLiveTiming>['rows'][number]) => ReactNode;
};

/**
 * The tint a lap flash gives the two lap time cells (#767). Race control and the referee screen flash only those
 * cells, so a lap flash does not read as an alert; the full row tint stays for close cars, a highlighted car and a jump start.
 */
function flashTint(flash: ReturnType<ReturnType<typeof useLapFlash>['get']>): string | false {
  return (
    (flash === 'race-best' && 'bg-race-best/30') ||
    (flash === 'personal-best' && 'bg-info/30') ||
    (flash === 'improving' && 'bg-success/30') ||
    (flash === 'slow' && 'bg-destructive/30')
  );
}

export function LiveTimingPanel({ raceId, status, highlightEntryIds, jumpStartEntryIds, showArrows = false, rowActions }: Props) {
  const { rows: liveRows, wsStatus, snapshotFailed } = useLiveTiming(raceId);
  const sorted = useMemo(() => holdBackLapped(liveRows), [liveRows]);
  const lappedEntryIds = useLappedBadge(sorted);
  const lapFlash = useLapFlash(sorted);
  const elim = useEliminations(raceId);
  const settings = useBoardSettings();
  const media = useRaceMedia(raceId);
  const sectors = useSectors(raceId);
  const sectorRows = useMemo(() => new Map(sectors.rows.map((r) => [r.entryId, r])), [sectors.rows]);
  const barClock = useLapBarClock(sorted, status, settings.lateMarginMs);
  const operatorIcons = useOperatorRowIcons(settings.hiddenRowIcons);
  const canMark = elim.shikabane && (status === 'RUNNING' || status === 'STOPPED');

  return (
    <RowIconsProvider shown={operatorIcons.shown}>
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">
          {status === 'STOPPED' ? 'Race Stopped' : 'Live Timing'}
        </h2>
        <div
          className={cn(
            'h-2 w-2 rounded-full',
            wsStatus === 'connected' ? 'bg-success animate-pulse' : 'bg-muted-foreground/40',
          )}
          title={wsStatus}
        />
        <span className="text-xs text-muted-foreground capitalize">{wsStatus}</span>
        <div className="ml-auto">
          <OperatorTimingControls
            allowed={operatorIcons.allowed}
            shown={operatorIcons.shown}
            onToggle={operatorIcons.toggle}
            onReset={operatorIcons.reset}
            customised={operatorIcons.customised}
            legendKinds={settings.closeGapMs > 0 ? ['lap', 'close', 'pace'] : ['lap', 'pace']}
          />
        </div>
      </div>

      {sorted.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {snapshotFailed
            ? 'The laps so far could not be loaded. They will show from the next passing, or refresh the page to try again.'
            : wsStatus === 'connected' ? 'Waiting for first passing…' : 'Connecting to timing…'}
        </p>
      ) : (
        <Table className="table-fixed short:[&_td]:py-1.5 short:[&_th]:h-8">
          <TableHeader>
            <TableRow>
              <TableHead className="w-14">Pos</TableHead>
              <TableHead className="w-16">Car</TableHead>
              <TableHead>Driver</TableHead>
              <TableHead className="w-16 text-right">Laps</TableHead>
              <TableHead className="hidden w-28 text-right xl:table-cell">Total Time</TableHead>
              <TableHead className="w-24 text-right">Last Lap</TableHead>
              <TableHead className="w-24 text-right">Best Lap</TableHead>
              {Array.from({ length: sectors.sectorCount }, (_, i) => (
                <TableHead key={`s${i}`} className="w-20 text-right">S{i + 1}</TableHead>
              ))}
              <TableHead className="w-24 text-right">Interval</TableHead>
              <TableHead className="w-24 text-right">Leader</TableHead>
              {elim.shikabane && <TableHead className="w-32 text-right">Shikabane</TableHead>}
              {rowActions && <TableHead className="w-44"><span className="sr-only">Actions</span></TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((row) => {
              const flash = lapFlash.get(row.entryId);
              const close = isClose(row, settings.closeGapMs);
              return (
              <TableRow
                key={row.entryId}
                className={cn(
                  'transition-colors duration-500',
                  highlightEntryIds?.has(row.entryId) && 'bg-chart-3/20',
                  !highlightEntryIds?.has(row.entryId) && close && 'bg-warning/15',
                  jumpStartEntryIds?.has(row.entryId) && 'bg-destructive/20',
                )}
              >
                <TableCell className="font-mono font-semibold">{row.position}{showArrows && <PositionArrow row={row} rows={sorted} />}</TableCell>
                <TableCell className="font-mono">{row.carNumber ?? '—'}</TableCell>
                <TableCell>
                  <div className="flex min-w-0 items-center overflow-hidden whitespace-nowrap">
                    <CarThumb
                      url={media.get(row.entryId)?.carPhotoUrl}
                      colours={carColoursFor(media.get(row.entryId)?.carColours, row.classColour)}
                      className="mr-2 shrink-0"
                    />
                    <span className="min-w-0 truncate">{row.driverName}</span>
                    {status === 'RUNNING' && <PaceIcon row={row} clock={barClock} className="ml-1" />}
                    <FlyingLapIcon row={row} className="ml-1" />
                    {/* The CLOSE badge below already says close, so only the lap flash gets an icon here */}
                    <RowStateIcon flash={flash} close={false} className="ml-2" />
                    {row.finished && <FinishedFlag />}
                    {close && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-warning text-warning">
                        CLOSE
                      </Badge>
                    )}
                    {elim.deadOrder.has(row.entryId) && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-destructive text-destructive">
                        DEAD #{elim.deadOrder.get(row.entryId)}
                      </Badge>
                    )}
                    {(elim.revivals.get(row.entryId) ?? 0) > 0 && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-chart-2 text-chart-2">
                        ZOMBIE{(elim.revivals.get(row.entryId) ?? 0) > 1 ? ` x${elim.revivals.get(row.entryId)}` : ''}
                      </Badge>
                    )}
                    {jumpStartEntryIds?.has(row.entryId) && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-destructive text-destructive">
                        JUMP START
                      </Badge>
                    )}
                    {lappedEntryIds.has(row.entryId) && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-chart-3 text-chart-3">
                        LAPPED
                      </Badge>
                    )}
                  </div>
                  {status === 'RUNNING' && <LapProgressBar row={row} clock={barClock} className="mt-1" />}
                </TableCell>
                <TableCell className="text-right font-mono">{row.lapsCompleted}</TableCell>
                <TableCell className="hidden text-right font-mono xl:table-cell">{fmtMs(row.totalTimeMs)}</TableCell>
                <TableCell className={cn('text-right font-mono transition-colors duration-500', flashTint(flash))}>{fmtMs(row.lastLapMs)}</TableCell>
                <TableCell className={cn('text-right font-mono transition-colors duration-500', flashTint(flash))}>{fmtMs(row.bestLapMs)}</TableCell>
                {Array.from({ length: sectors.sectorCount }, (_, i) => {
                  const sectorRow = sectorRows.get(row.entryId);
                  const tone = sectorRow ? sectorTone(sectorRow, sectors, i) : 'normal';
                  return (
                    <TableCell
                      key={`s${i}`}
                      className={cn(
                        'text-right font-mono',
                        tone === 'overall-best' && 'font-semibold text-race-best',
                        tone === 'personal-best' && 'text-success',
                        !sectorRow && 'text-muted-foreground',
                      )}
                    >
                      {sectorRow ? fmtMs(sectorRow.lastMs[i]) : '—'}
                    </TableCell>
                  );
                })}
                <TableCell className="text-right font-mono text-muted-foreground">
                  {gapLabel(row.position, row.intervalLapsDown, row.gapToAheadMs)}
                </TableCell>
                <TableCell className="text-right font-mono text-muted-foreground">
                  {gapLabel(row.position, row.lapsDown, row.gapToLeaderMs)}
                </TableCell>
                {elim.shikabane && (
                  <TableCell className="text-right">
                    {canMark && <EliminationButtons entryId={row.entryId} elim={elim} />}
                  </TableCell>
                )}
                {rowActions && <TableCell className="text-right">{rowActions(row)}</TableCell>}
              </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
    </RowIconsProvider>
  );
}
