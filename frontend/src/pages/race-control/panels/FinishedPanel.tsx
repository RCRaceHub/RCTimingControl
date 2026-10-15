import { Link } from 'react-router-dom';
import { useResultSnapshot } from '@/hooks/race-control/useResultSnapshot';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Printer } from 'lucide-react';
import { fmtMs } from '@/lib/format';
import { formatTime } from '@/lib/dates';
import { FinishedFlag } from '@/components/FinishedFlag';

type Props = {
  raceId: number;
  eventId: number;
  onRestart?: () => void;
  isRestarting?: boolean;
};

export function FinishedPanel({ raceId, eventId, onRestart, isRestarting }: Props) {
  const { data, isLoading, error } = useResultSnapshot(raceId);

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="space-y-2">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-6 bg-muted animate-pulse rounded" />
          ))}
        </div>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card className="p-6">
        <p className="text-sm text-muted-foreground">
          {error ? 'Could not load results.' : 'No results available yet.'}
        </p>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Results — {data.raceLabel}</h2>
          <p className="text-xs text-muted-foreground">
            Finished {formatTime(data.finishedAt, true)}
          </p>
        </div>
        <div className="flex gap-2">
          {onRestart && (
            <Button variant="outline" size="sm" onClick={onRestart} disabled={isRestarting}>
              {isRestarting ? 'Restarting…' : 'Restart Race'}
            </Button>
          )}
          <Button variant="outline" size="sm" asChild>
            <Link to={`/race-control/event/${eventId}/results/${raceId}`} target="_blank">
              <Printer className="h-4 w-4 mr-1.5" />
              Print
            </Link>
          </Button>
        </div>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">Pos</TableHead>
            <TableHead>Driver</TableHead>
            <TableHead>Car</TableHead>
            <TableHead className="text-right">Laps</TableHead>
            <TableHead className="text-right">Total Time</TableHead>
            <TableHead className="text-right">Best Lap</TableHead>
            <TableHead className="text-right">Gap</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.positions.map((row) => (
            <TableRow key={row.entryId}>
              <TableCell className="font-semibold">{row.position}</TableCell>
              <TableCell>{row.driverName}<FinishedFlag /></TableCell>
              <TableCell>{row.carNumber ?? '—'}</TableCell>
              <TableCell className="text-right font-mono">{row.lapsCompleted}</TableCell>
              <TableCell className="text-right font-mono">{fmtMs(row.totalTimeMs)}</TableCell>
              <TableCell className="text-right font-mono">{fmtMs(row.bestLapMs)}</TableCell>
              <TableCell className="text-right font-mono text-muted-foreground">
                {row.position === 1 ? '—' : fmtMs(row.gapToLeaderMs)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
