import { useEffect } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useRaceResult } from '@/hooks/race-control/useEventResults';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getRaceHistory, type ResultSnapshotDto } from '@/lib/raceControlApi';
import { lapSheetColumns, positionSeries } from '@/lib/heatSheet';
import { PositionChart } from '@/components/print/PositionChart';
import { HeatResultTable } from '@/components/print/HeatResultTable';
import { SheetHeader } from '@/components/print/SheetHeader';
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
  if (columns.length === 0) {
    return <p className="mt-4 text-sm text-muted-foreground">No lap times were recorded.</p>;
  }
  const lastLap = columns.reduce((most, col) => Math.max(most, col.laps.at(-1)?.lapNumber ?? 0), 0);
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
          {Array.from({ length: lastLap }, (_, i) => i + 1).map((lapNumber) => (
            <tr key={lapNumber} className="border-b border-border/50 print:break-inside-avoid">
              <th scope="row" className="py-0.5 pr-2 text-left font-normal text-muted-foreground">{lapNumber}</th>
              {columns.map((col) => {
                const lap = col.laps.find((l) => l.lapNumber === lapNumber);
                return (
                  <td
                    key={col.entryId}
                    className={`py-0.5 pr-2 text-right font-mono ${lapNumber === col.fastestLap ? 'font-bold' : ''}`}
                  >
                    {lap ? fmtMs(lap.ms) : ''}
                  </td>
                );
              })}
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

  const { data: eventResults, isLoading, error } = useRaceResult(raceId || null);
  const race = eventResults?.races[0];
  const data = race?.result;
  const [searchParams, setSearchParams] = useSearchParams();
  /** Sets or clears one option in the page address, keeping the others, so a reload keeps the ticks. */
  function setOption(key: string, value: string | null) {
    const next = new URLSearchParams(searchParams);
    if (value === null) next.delete(key);
    else next.set(key, value);
    setSearchParams(next, { replace: true });
  }
  const showLaps = searchParams.get('laps') === '1';
  // The position chart is on unless the tick is cleared (`chart=0`)
  const showChart = searchParams.get('chart') !== '0';
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

  // Only offered when the heat has places to draw: a chart of flat lines says nothing
  const chartSeries = data ? positionSeries(data.positions, data.lapHistory) : [];

  if (isLoading) {
    return (
      <div className="p-8 text-sm text-muted-foreground">Loading results…</div>
    );
  }

  if (error || !eventResults || !race || !data) {
    return (
      <div className="p-8 text-sm text-destructive">
        {error ? 'Could not load results.' : 'No results available.'}
      </div>
    );
  }

  return (
    <div className="p-8 max-w-3xl mx-auto print:p-4">
      <SheetHeader
        title={data.raceLabel}
        eventName={eventResults.eventName}
        eventDate={eventResults.eventDate}
        branding={data.clubBranding}
      />
      <p className="mb-4 text-sm text-muted-foreground">Finished {formatDateTime(data.finishedAt)}</p>

      <HeatResultTable race={race} />

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

      {showChart && chartSeries.length > 0 && (
        <section aria-label="Position chart" className="mt-6">
          <h2 className="mb-2 text-sm font-semibold">Position by lap</h2>
          <PositionChart series={chartSeries} />
        </section>
      )}

      {showLaps && <LapSheet positions={data.positions} lapHistory={data.lapHistory} />}

      <div className="mt-6 flex items-center gap-4 print:hidden">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={showLaps}
            onChange={(e) => setOption('laps', e.target.checked ? '1' : null)}
          />
          Include lap-by-lap times
        </label>
        {chartSeries.length > 0 && (
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={showChart}
              onChange={(e) => setOption('chart', e.target.checked ? null : '0')}
            />
            Include position chart
          </label>
        )}
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
