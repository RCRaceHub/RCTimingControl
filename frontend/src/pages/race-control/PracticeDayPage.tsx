import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChevronLeft, Loader2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { getPracticeDay } from '@/lib/practiceApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { fmtMs } from '@/lib/format';

const DEFAULT_N = 5;

/** Today as YYYY-MM-DD in the browser's own zone, which is the club's. */
function today(): string {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

/** A driver's practice added up across every session of a day (#151). */
export function PracticeDayPage() {
  const [date, setDate] = useState(today);
  const [nText, setNText] = useState(String(DEFAULT_N));
  const parsed = Number(nText);
  const n = Number.isInteger(parsed) && parsed >= 1 ? parsed : null;

  const { data, isLoading, isError } = useQuery({
    queryKey: raceControlQueryKeys.practice.day(date, n ?? 0),
    queryFn: () => getPracticeDay(date, n ?? undefined),
    enabled: date !== '' && n !== null,
    placeholderData: keepPreviousData,
  });

  return (
    <div className="flex flex-col h-full">
      <header className="flex flex-wrap items-center gap-3 px-4 py-3 border-b shrink-0">
        <Link
          to="/admin/race-control"
          className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
          aria-label="Back"
        >
          <ChevronLeft className="h-4 w-4" />
        </Link>
        <h1 className="text-base font-semibold">Practice day</h1>
        <label className="flex items-center gap-2 text-sm ml-auto">
          Date
          <Input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="w-40"
          />
        </label>
        <label className="flex items-center gap-2 text-sm">
          Laps to average
          <Input
            type="number"
            min={1}
            step={1}
            value={nText}
            onChange={(e) => setNText(e.target.value)}
            className="w-20"
          />
        </label>
      </header>

      <main className="flex-1 overflow-y-auto p-4 space-y-4">
        {n === null && (
          <p className="text-sm text-muted-foreground text-center py-4">
            Enter a whole number of laps to average, 1 or more.
          </p>
        )}
        {isLoading && (
          <div className="flex items-center gap-2 py-8 justify-center text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            <span className="text-sm">Loading the day…</span>
          </div>
        )}
        {isError && (
          <p className="text-sm text-destructive text-center py-8">Could not load the day's practice.</p>
        )}
        {data && data.sessions.length === 0 && (
          <p className="text-sm text-muted-foreground text-center py-8">No practice sessions started on this day.</p>
        )}
        {data && data.sessions.length > 0 && (
          <>
            <p className="text-xs text-muted-foreground">
              {data.sessions.length} {data.sessions.length === 1 ? 'session' : 'sessions'}:{' '}
              {data.sessions.map((s) => s.name).join(', ')}. The average is the best {data.bestLapN} laps
              in one run.
            </p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Racer</TableHead>
                  <TableHead className="w-28">Transponder</TableHead>
                  <TableHead className="w-20">Sessions</TableHead>
                  <TableHead className="w-16">Runs</TableHead>
                  <TableHead className="w-16">Laps</TableHead>
                  <TableHead className="w-28">Best Lap</TableHead>
                  <TableHead className="w-36">Best {data.bestLapN} Laps</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.rows.map((row) => (
                  <TableRow key={row.competitorId ?? `t-${row.transponderNumbers}`}>
                    <TableCell className={row.name ? 'font-medium' : 'text-muted-foreground'}>
                      {row.name ?? 'Unknown'}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{row.transponderNumbers}</TableCell>
                    <TableCell>{row.sessions}</TableCell>
                    <TableCell>{row.runs}</TableCell>
                    <TableCell>{row.laps}</TableCell>
                    <TableCell className="font-mono">{fmtMs(row.bestLapMs)}</TableCell>
                    <TableCell className="font-mono">
                      {row.bestConsecutiveNMs !== null
                        ? fmtMs(Math.round(row.bestConsecutiveNMs / data.bestLapN)) + ' avg'
                        : '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </>
        )}
      </main>
    </div>
  );
}
