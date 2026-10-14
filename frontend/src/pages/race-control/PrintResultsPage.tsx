import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useResultSnapshot } from '@/hooks/race-control/useResultSnapshot';
import { fmtMs } from '@/lib/format';
import { formatDateTime } from '@/lib/dates';

export default function PrintResultsPage() {
  const { raceId: raceIdStr } = useParams<{ raceId: string }>();
  const raceId = Number(raceIdStr);

  const { data, isLoading, error } = useResultSnapshot(raceId || null);

  useEffect(() => {
    if (data) {
      document.title = `Results — ${data.raceLabel}`;
    }
  }, [data]);

  if (isLoading) {
    return (
      <div className="p-8 text-sm text-muted-foreground">Loading results…</div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-8 text-sm text-destructive">
        {error ? 'Could not load results.' : 'No results available.'}
      </div>
    );
  }

  return (
    <div className="p-8 max-w-3xl mx-auto print:p-4">
      <div className="mb-6">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold">{data.raceLabel}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              {data.clubBranding?.clubName} &bull; Finished{' '}
              {formatDateTime(data.finishedAt)}
            </p>
          </div>
          {data.clubBranding?.logoUrl && (
            <img
              src={data.clubBranding.logoUrl}
              alt={data.clubBranding?.clubName}
              className="h-16 object-contain"
            />
          )}
        </div>
      </div>

      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b-2 border-foreground">
            <th className="text-left py-2 pr-4 w-10">Pos</th>
            <th className="text-left py-2 pr-4">Driver</th>
            <th className="text-left py-2 pr-4">Car</th>
            <th className="text-right py-2 pr-4">Laps</th>
            <th className="text-right py-2 pr-4">Total Time</th>
            <th className="text-right py-2 pr-4">Best Lap</th>
            <th className="text-right py-2">Gap</th>
          </tr>
        </thead>
        <tbody>
          {data.positions.map((row) => (
            <tr key={row.entryId} className="border-b border-border/50">
              <td className="py-1.5 pr-4 font-semibold">{row.position}</td>
              <td className="py-1.5 pr-4">{row.driverName}</td>
              <td className="py-1.5 pr-4 text-muted-foreground">{row.carNumber ?? '—'}</td>
              <td className="py-1.5 pr-4 text-right font-mono">{row.lapsCompleted}</td>
              <td className="py-1.5 pr-4 text-right font-mono">{fmtMs(row.totalTimeMs)}</td>
              <td className="py-1.5 pr-4 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
              <td className="py-1.5 text-right font-mono text-muted-foreground">
                {row.position === 1 ? '—' : fmtMs(row.gapToLeaderMs)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="mt-6 print:hidden">
        <button
          onClick={() => window.print()}
          className="px-4 py-2 bg-primary text-primary-foreground rounded text-sm"
        >
          Print
        </button>
      </div>
    </div>
  );
}
