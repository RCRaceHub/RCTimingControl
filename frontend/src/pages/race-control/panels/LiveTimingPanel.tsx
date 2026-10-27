import { useMemo } from 'react';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { useLappedBadge } from '@/hooks/race-control/useLappedBadge';
import { useLapFlash } from '@/hooks/race-control/useLapFlash';
import { TimingLegend } from '@/components/TimingLegend';
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
import { PositionArrow } from '@/components/PositionArrow';
import { RowStateIcon } from '@/components/StateIcon';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import { holdBackLapped, isClose } from '@/lib/lapProgress';
import { CarThumb } from '@/components/CarThumb';
import { useRaceMedia } from '@/hooks/useRaceMedia';
import { useBoardSettings } from '@/hooks/useBoardSettings';

type Props = {
  raceId: number;
  status: RunOrderItemDto['status'];
  /** Entry IDs to highlight (e.g. proximity alerts from referee view). */
  highlightEntryIds?: Set<number>;
  /** Entry IDs that jumped a stagger start, flagged on their row. */
  jumpStartEntryIds?: Set<number>;
  /** Show the places gained or lost against the grid beside each position; for finals. */
  showArrows?: boolean;
};

export function LiveTimingPanel({ raceId, status, highlightEntryIds, jumpStartEntryIds, showArrows = false }: Props) {
  const { rows: liveRows, wsStatus, snapshotFailed } = useLiveTiming(raceId);
  const sorted = useMemo(() => holdBackLapped(liveRows), [liveRows]);
  const lappedEntryIds = useLappedBadge(sorted);
  const lapFlash = useLapFlash(sorted);
  const elim = useEliminations(raceId);
  const settings = useBoardSettings();
  const media = useRaceMedia(raceId);
  const barClock = useLapBarClock(sorted, status, settings.lateMarginMs);
  const canMark = elim.shikabane && (status === 'RUNNING' || status === 'STOPPED');

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">
          {status === 'STOPPED' ? 'Race Stopped' : 'Live Timing'}
        </h2>
        <div
          className={cn(
            'h-2 w-2 rounded-full',
            wsStatus === 'connected' ? 'bg-green-500 animate-pulse' : 'bg-muted-foreground/40',
          )}
          title={wsStatus}
        />
        <span className="text-xs text-muted-foreground capitalize">{wsStatus}</span>
      </div>

      {sorted.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          {snapshotFailed
            ? 'The laps so far could not be loaded. They will show from the next passing, or refresh the page to try again.'
            : wsStatus === 'connected' ? 'Waiting for first passing…' : 'Connecting to timing…'}
        </p>
      ) : (
        <Table className="table-fixed">
          <TableHeader>
            <TableRow>
              <TableHead className="w-14">Pos</TableHead>
              <TableHead className="w-16">Car</TableHead>
              <TableHead>Driver</TableHead>
              <TableHead className="w-16 text-right">Laps</TableHead>
              <TableHead className="w-28 text-right">Total Time</TableHead>
              <TableHead className="w-24 text-right">Last Lap</TableHead>
              <TableHead className="w-24 text-right">Best Lap</TableHead>
              <TableHead className="w-24 text-right">Interval</TableHead>
              <TableHead className="w-24 text-right">Leader</TableHead>
              {elim.shikabane && <TableHead className="w-32 text-right">Shikabane</TableHead>}
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
                  flash === 'race-best'      && 'bg-purple-500/30',
                  flash === 'personal-best'  && 'bg-blue-500/30',
                  flash === 'improving'      && 'bg-green-500/30',
                  flash === 'slow'           && 'bg-red-500/30',
                  !flash && highlightEntryIds?.has(row.entryId) && 'bg-chart-3/20',
                  !flash && !highlightEntryIds?.has(row.entryId) && close && 'bg-amber-500/15',
                  jumpStartEntryIds?.has(row.entryId) && 'bg-destructive/20',
                )}
              >
                <TableCell className="font-mono font-semibold">{row.position}{showArrows && <PositionArrow row={row} rows={sorted} />}</TableCell>
                <TableCell className="font-mono">{row.carNumber ?? '—'}</TableCell>
                <TableCell>
                  <div className="flex min-w-0 items-center overflow-hidden whitespace-nowrap">
                    <CarThumb url={media.get(row.entryId)?.carPhotoUrl} className="mr-2 shrink-0" />
                    <span className="min-w-0 truncate">{row.driverName}</span>
                    {/* The CLOSE badge below already says close, so only the lap flash gets an icon here */}
                    <RowStateIcon flash={flash} close={false} className="ml-2" />
                    {row.finished && <FinishedFlag />}
                    {close && (
                      <Badge variant="outline" className="ml-2 shrink-0 text-[10px] px-1 py-0 border-amber-500 text-amber-500">
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
                <TableCell className="text-right font-mono">{fmtMs(row.totalTimeMs)}</TableCell>
                <TableCell className="text-right font-mono">{fmtMs(row.lastLapMs)}</TableCell>
                <TableCell className="text-right font-mono">{fmtMs(row.bestLapMs)}</TableCell>
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
              </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
      {sorted.length > 0 && (
        <TimingLegend kinds={settings.closeGapMs > 0 ? ['lap', 'close'] : ['lap']} className="mt-2 text-muted-foreground" />
      )}
    </div>
  );
}
