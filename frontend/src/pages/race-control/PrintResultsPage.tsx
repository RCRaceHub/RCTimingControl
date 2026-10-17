import { useEffect } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useResultSnapshot } from '@/hooks/race-control/useResultSnapshot';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getRaceHistory, type ResultSnapshotDto } from '@/lib/raceControlApi';
import { averageLapMs, lapSheetColumns, lapTimesFor } from '@/lib/heatSheet';
import { fmtMs } from '@/lib/format';
import { formatDateTime } from '@/lib/dates';

/** Every lap time, one column per driver in finishing order, each driver's quickest lap in bold. */
function LapSheet({
  positions,
  lapHistory,
}: {
  positions: ResultSnapshotDto['positions'];
  lapHistory: ResultSnapshotDto['lapHistory'];
}) {
  const columns = lapSheetColumns(positions, lapHistory);
  const rows = columns.reduce((most, col) => Math.max(most, col.laps.length), 0);
  if (columns.length === 0) {
    return <p className="mt-4 text-sm text-muted-foreground">No lap times were recorded.</p>;
  }
  return (
    <section aria-label="Lap times" className="mt-6 break-before-page text-xs">
      <h2 className="mb-2 text-sm font-semibold">Lap times</h2>
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b-2 border-foreground">
            <th scope="col" className="py-1 pr-2 text-left">Lap</th>
            {columns.map((col) => (
              <th key={col.entryId} scope="col" className="py-1 pr-2 text-right">
                {col.carNumber ? `${col.carNumber} ` : ''}
                {col.driverName}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, i) => (
            <tr key={i} className="border-b border-border/50">
              <th scope="row" className="py-0.5 pr-2 text-left font-normal text-muted-foreground">{i + 1}</th>
              {columns.map((col) => (
                <td
                  key={col.entryId}
                  className={`py-0.5 pr-2 text-right font-mono ${i === col.fastestIndex ? 'font-bold' : ''}`}
                >
                  {i < col.laps.length ? fmtMs(col.laps[i]) : ''}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-2 text-muted-foreground">Bold is the driver's quickest lap.</p>
    </section>
  );
}

export default function PrintResultsPage() {
  const { raceId: raceIdStr } = useParams<{ raceId: string }>();
  const raceId = Number(raceIdStr);

  const { data, isLoading, error } = useResultSnapshot(raceId || null);
  const [searchParams, setSearchParams] = useSearchParams();
  const showLaps = searchParams.get('laps') === '1';
  // The penalties are a nicety: if they can't be read the result still prints
  const history = useQuery({
    queryKey: raceControlQueryKeys.raceHistory(raceId),
    queryFn: () => getRaceHistory(raceId),
    enabled: raceId > 0,
    staleTime: 30_000,
  });
  const penalties = (history.data ?? []).filter((item) => item.kind === 'PENALTY');

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
            <th className="text-right py-2 pr-4">Avg Lap</th>
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
              <td className="py-1.5 pr-4 text-right font-mono">
                {fmtMs(averageLapMs(lapTimesFor(row.entryId, data.lapHistory)))}
              </td>
              <td className="py-1.5 text-right font-mono text-muted-foreground">
                {row.position === 1 ? '—' : fmtMs(row.gapToLeaderMs)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {penalties.length > 0 && (
        <section aria-label="Penalties" className="mt-4 text-sm">
          <h2 className="font-semibold">Penalties</h2>
          <ul className="list-disc pl-5">
            {penalties.map((p, i) => (
              <li key={i}>
                {p.driver ?? 'Unknown driver'}: {p.summary}
              </li>
            ))}
          </ul>
        </section>
      )}

      {showLaps && <LapSheet positions={data.positions} lapHistory={data.lapHistory} />}

      <div className="mt-6 flex items-center gap-4 print:hidden">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={showLaps}
            onChange={(e) =>
              setSearchParams(e.target.checked ? { laps: '1' } : {}, { replace: true })
            }
          />
          Include lap-by-lap times
        </label>
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
