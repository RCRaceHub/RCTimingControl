import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { PracticeTimingRowDto } from '@/lib/practiceApi';
import { fmtMs } from '@/lib/format';

interface PracticeLiveTableProps {
  rows: PracticeTimingRowDto[];
  bestLapN: number;
  /** Offers a button to name an unknown transponder; left out, rows can't be named */
  onName?: (transponderNumber: string) => void;
}

export function PracticeLiveTable({ rows, bestLapN, onName }: PracticeLiveTableProps) {
  if (rows.length === 0) {
    return (
      <div className="text-sm text-muted-foreground text-center py-8">
        Waiting for transponders…
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-12">Pos</TableHead>
          <TableHead className="flex-1">Racer</TableHead>
          <TableHead className="w-16">Laps</TableHead>
          <TableHead className="w-16" title="A long pause between laps starts a new run">Runs</TableHead>
          <TableHead className="w-28">Best Lap</TableHead>
          <TableHead
            className="w-36"
            title={`Best average over ${bestLapN} consecutive laps in one run`}
          >
            Best {bestLapN} Laps
          </TableHead>
          <TableHead className="w-28">Last Lap</TableHead>
          <TableHead className="w-40">Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            key={row.transponderNumber}
            className={
              row.isUnknown
                ? 'bg-[var(--flag-red)]/5'
                : row.laps > 0 && row.position === 1
                  ? 'bg-yellow-500/10'
                  : row.laps > 0 && row.position === 2
                    ? 'bg-slate-400/10'
                    : row.laps > 0 && row.position === 3
                      ? 'bg-amber-700/10'
                      : ''
            }
          >
            <TableCell className="text-xl font-semibold">
              {row.laps > 0 ? row.position : '—'}
            </TableCell>
            <TableCell
              className={
                row.isUnknown
                  ? 'font-mono text-xs text-muted-foreground'
                  : ''
              }
            >
              {row.racerName}
            </TableCell>
            <TableCell className="font-mono tabular-nums">{row.laps}</TableCell>
            <TableCell className="font-mono tabular-nums">{row.runs}</TableCell>
            <TableCell className="font-mono tabular-nums">
              {fmtMs(row.bestLapMs)}
            </TableCell>
            <TableCell className="font-mono tabular-nums">
              {row.bestConsecutiveNMs !== null
                ? fmtMs(Math.round(row.bestConsecutiveNMs / bestLapN)) + ' avg'
                : '—'}
            </TableCell>
            <TableCell className="font-mono tabular-nums">
              {fmtMs(row.lastLapMs)}
            </TableCell>
            <TableCell>
              {row.isUnknown ? (
                <>
                  <Badge
                    variant="outline"
                    className="border-destructive text-destructive"
                  >
                    Unknown
                  </Badge>
                  {onName && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="ml-2"
                      onClick={() => onName(row.transponderNumber)}
                    >
                      Name
                    </Button>
                  )}
                </>
              ) : (
                <Badge variant="outline">Known</Badge>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
