import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { useLappedBadge } from '@/hooks/race-control/useLappedBadge';
import { useLapFlash } from '@/hooks/race-control/useLapFlash';
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
import { cn } from '@/lib/utils';
import { fmtMs } from '@/lib/format';
import { FinishedFlag } from '@/components/FinishedFlag';

type Props = {
  raceId: number;
  status: RunOrderItemDto['status'];
  /** Entry IDs to highlight (e.g. proximity alerts from referee view). */
  highlightEntryIds?: Set<number>;
};

export function LiveTimingPanel({ raceId, status, highlightEntryIds }: Props) {
  const { rows: sorted, wsStatus, snapshotFailed } = useLiveTiming(raceId);
  const lappedEntryIds = useLappedBadge(sorted);
  const lapFlash = useLapFlash(sorted);

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
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">Pos</TableHead>
              <TableHead>Car</TableHead>
              <TableHead>Driver</TableHead>
              <TableHead className="text-right">Laps</TableHead>
              <TableHead className="text-right">Last Lap</TableHead>
              <TableHead className="text-right">Best Lap</TableHead>
              <TableHead className="text-right">Interval</TableHead>
              <TableHead className="text-right">Leader</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((row) => {
              const flash = lapFlash.get(row.entryId);
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
                )}
              >
                <TableCell className="font-mono font-semibold">{row.position}</TableCell>
                <TableCell className="font-mono">{row.carNumber ?? '—'}</TableCell>
                <TableCell>
                  <span>{row.driverName}</span>
                  {row.finished && <FinishedFlag />}
                  {lappedEntryIds.has(row.entryId) && (
                    <Badge variant="outline" className="ml-2 text-[10px] px-1 py-0 border-chart-3 text-chart-3">
                      LAPPED
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-right font-mono">{row.lapsCompleted}</TableCell>
                <TableCell className="text-right font-mono">{fmtMs(row.lastLapMs)}</TableCell>
                <TableCell className="text-right font-mono">{fmtMs(row.bestLapMs)}</TableCell>
                <TableCell className="text-right font-mono text-muted-foreground">
                  {row.position === 1 ? '—'
                    : row.intervalLapsDown > 0 ? `+${row.intervalLapsDown} Lap${row.intervalLapsDown > 1 ? 's' : ''}`
                    : fmtMs(row.gapToAheadMs)}
                </TableCell>
                <TableCell className="text-right font-mono text-muted-foreground">
                  {row.position === 1 ? '—'
                    : row.lapsDown > 0 ? `+${row.lapsDown} Lap${row.lapsDown > 1 ? 's' : ''}`
                    : fmtMs(row.gapToLeaderMs)}
                </TableCell>
              </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
