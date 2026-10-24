import { Loader2, AlertCircle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { useChampionshipStandings } from '@/hooks/admin/useAdminChampionships';
import type { RoundResultDto } from '@/lib/adminApi';

interface Props {
  championshipId: number;
}

/**
 * One driver's result for a round: a dash when they have none, EXC when excluded, DNS for position 0 (on the grid but
 * no result), otherwise the points, struck through when the round was dropped from the total (best X from Y).
 */
function RoundCell({ result }: { result: RoundResultDto | undefined }) {
  if (!result) return <TableCell className="text-center text-muted-foreground">—</TableCell>;
  if (result.excluded) {
    return (
      <TableCell className="text-center">
        <Badge variant="destructive" className="text-xs">EXC</Badge>
      </TableCell>
    );
  }
  if (result.position === 0) {
    return (
      <TableCell className="text-center">
        <span className="text-xs text-muted-foreground">DNS</span>
      </TableCell>
    );
  }
  return (
    <TableCell
      className={`text-center text-sm ${result.dropped ? 'text-muted-foreground line-through' : ''}`}
      title={result.dropped ? 'This round was dropped from the total' : undefined}
    >
      {result.points}
    </TableCell>
  );
}

export function ChampionshipStandingsTable({ championshipId }: Props) {
  const { data: rows, isLoading, isError } = useChampionshipStandings(championshipId);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-8 text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-sm">Loading standings…</span>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center gap-2 py-8 text-destructive">
        <AlertCircle className="h-4 w-4" />
        <span className="text-sm">Failed to load standings.</span>
      </div>
    );
  }

  if (!rows || rows.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        No standings available yet — race results not recorded.
      </p>
    );
  }

  const roundNumbers = [...new Set(rows.flatMap(r => r.rounds.map(rr => rr.roundNumber)))].sort(
    (a, b) => a - b
  );

  return (
    <div className="rounded-lg border overflow-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">#</TableHead>
            <TableHead>Driver</TableHead>
            <TableHead>Class</TableHead>
            <TableHead className="text-right">Total</TableHead>
            {roundNumbers.map(n => (
              <TableHead key={n} className="text-center w-16">
                R{n}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const roundMap = new Map(row.rounds.map(r => [r.roundNumber, r]));
            return (
              <TableRow key={`${row.category ?? ''}-${row.racingClassId}-${row.driverId}`}>
                <TableCell className="font-medium">{row.position}</TableCell>
                <TableCell>{row.displayName}</TableCell>
                <TableCell>
                  {row.category ? `${row.category}: ` : ''}
                  {row.racingClassId === 0 ? 'Overall' : `Class ${row.racingClassId}`}
                </TableCell>
                <TableCell className="text-right font-semibold">{row.totalPoints}</TableCell>
                {roundNumbers.map(n => (
                  <RoundCell key={n} result={roundMap.get(n)} />
                ))}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
