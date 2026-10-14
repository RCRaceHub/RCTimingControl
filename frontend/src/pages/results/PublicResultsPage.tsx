import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { usePublicResultSnapshot } from '@/hooks/race-control/usePublicResultSnapshot';
import type { PositionAtLap } from '@/lib/raceControlApi';
import { fmtMs } from '@/lib/format';
import { formatDateTime } from '@/lib/dates';
import { getApiErrorStatus } from '@/lib/errors';

function LapTimesPanel({ entryId, lapHistory }: { entryId: number; lapHistory: PositionAtLap[] }) {
  const laps = lapHistory.filter(l => l.entryId === entryId);
  if (laps.length === 0) {
    return <p className="text-xs text-muted-foreground py-1">No lap data recorded.</p>;
  }
  const formatLapTime = (ms: number | null | undefined): string => {
    if (ms === null || ms === undefined) return '—';
    return (ms / 1000).toFixed(3) + 's';
  };
  return (
    <table className="text-xs font-mono w-full">
      <thead>
        <tr className="text-muted-foreground">
          <th className="text-left font-normal pr-4 py-0.5">Lap</th>
          <th className="text-right font-normal pr-4 py-0.5">Time</th>
          <th className="text-right font-normal py-0.5">Pos</th>
        </tr>
      </thead>
      <tbody>
        {laps.map(l => (
          <tr key={l.lapNumber}>
            <td className="pr-4 text-muted-foreground py-0.5">Lap {l.lapNumber}</td>
            <td
              className="pr-4 text-right py-0.5"
              data-ms={l.lapTimeMs ?? ''}
            >{formatLapTime(l.lapTimeMs)}</td>
            <td className="text-right py-0.5">P{l.position}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function PublicResultsPage() {
  const { raceId: raceIdStr } = useParams<{ raceId: string }>();
  const raceId = raceIdStr ? Number(raceIdStr) : null;

  const { data, isLoading, error } = usePublicResultSnapshot(raceId);

  const [expandedEntryId, setExpandedEntryId] = useState<number | null>(null);

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

  // A 4xx means there is nothing to show for this race (a 404, or a malformed id); anything else is the connection
  const status = error ? getApiErrorStatus(error) : undefined;
  if (error && !(status != null && status >= 400 && status < 500)) {
    return (
      <div className="p-8 text-sm text-destructive">
        The results could not be loaded. Check the connection and refresh the page.
      </div>
    );
  }

  if (!data) {
    return (
      <div className="p-8 text-sm text-destructive">
        Results are not available yet. The race has not finished.
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
              {data.clubBranding?.clubName ?? ''}{data.clubBranding ? ' • ' : ''}Finished{' '}
              {formatDateTime(data.finishedAt)}
            </p>
          </div>
          {data.clubBranding?.logoUrl && (
            <img
              src={data.clubBranding.logoUrl}
              alt={data.clubBranding.clubName ?? ''}
              className="h-16 object-contain"
            />
          )}
        </div>
      </div>

      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b-2 border-foreground">
            <th className="text-left text-sm font-semibold py-2 pr-4 w-10">Pos</th>
            <th className="text-left text-sm font-semibold py-2 pr-4">Driver</th>
            <th className="text-right text-sm font-semibold py-2 pr-4">Car#</th>
            <th className="text-right text-sm font-semibold py-2 pr-4">Laps</th>
            <th className="text-right text-sm font-semibold py-2 pr-4">Time</th>
            <th className="text-right text-sm font-semibold py-2 pr-4">Best</th>
            <th className="w-6" />
          </tr>
        </thead>
        <tbody>
          {data.positions.map((row) => (
            <React.Fragment key={row.entryId}>
              <tr
                className={`border-b border-border/50 cursor-pointer select-none hover:bg-muted/30 ${row.position === 1 ? 'text-primary font-bold' : ''}`}
                style={{ minHeight: '44px' }}
                onClick={() => setExpandedEntryId(expandedEntryId === row.entryId ? null : row.entryId)}
              >
                <td className="py-2 pr-4 font-mono">{row.position}</td>
                <td className="py-2 pr-4">{row.driverName}</td>
                <td className="py-2 pr-4 text-right font-mono">{row.carNumber ?? '—'}</td>
                <td className="py-2 pr-4 text-right font-mono">{row.lapsCompleted}</td>
                <td className="py-2 pr-4 text-right font-mono">
                  {row.totalTimeMs > 0 ? fmtMs(row.totalTimeMs) : '—'}
                </td>
                <td className="py-2 pr-4 text-right font-mono">{fmtMs(row.bestLapMs)}</td>
                <td className="py-2 pl-2 text-right text-muted-foreground text-xs">
                  {expandedEntryId === row.entryId ? '▲' : '▼'}
                </td>
              </tr>
              {expandedEntryId === row.entryId && (
                <tr key={`${row.entryId}-laps`} className="bg-muted/30">
                  <td colSpan={7} className="px-4 py-2">
                    <LapTimesPanel entryId={row.entryId} lapHistory={data.lapHistory} />
                  </td>
                </tr>
              )}
            </React.Fragment>
          ))}
        </tbody>
      </table>

      <div className="mt-6 print:hidden">
        <button
          onClick={() => window.print()}
          className="px-4 py-2 bg-primary text-primary-foreground rounded text-sm"
        >
          Print Results
        </button>
      </div>
    </div>
  );
}
