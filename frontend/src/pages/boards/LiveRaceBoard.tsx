// The Live Race screen for a venue TV: the race on track with its live timing table and the position chart (#410),
// who holds which place lap by lap. Anonymous like the other boards, and follows the race live through useBoardRace.
// Now and next keeps to the line-up and what is coming, so the timing and the chart live here.
import { useSearchParams } from 'react-router-dom';
import { PositionChart } from '@/components/print/PositionChart';
import { BoardLiveTable } from './BoardLiveTable';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { parseEventParam } from './boardFormat';
import { useServerNow } from '@/hooks/race-control/useServerNow';
import { DEFAULT_CLOSE_MS } from '@/lib/lapProgress';
import { useBoardRace } from './useBoardRace';
import { useLivePositionSeries } from './useLivePositionSeries';

export default function LiveRaceBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { nowNext, isPending, isError, currentRace, raceId, rows } = useBoardRace(eventId);
  const series = useLivePositionSeries(raceId, rows);

  // Options on the address: bars=hide leaves out the lap bars, close=SECONDS sets how near counts as close (0 for off)
  const showBars = searchParams.get('bars') !== 'hide';
  const closeParam = Number(searchParams.get('close'));
  const closeMs = searchParams.has('close') && Number.isFinite(closeParam) ? Math.max(0, closeParam) * 1000 : DEFAULT_CLOSE_MS;
  const status = currentRace?.status;
  // The bars compare passing times, stamped by the timing laptop, with this device's clock
  const nowMs = useServerNow(0, showBars && status === 'RUNNING');

  if (isPending) {
    return (
      <BoardShell>
        <BoardMessage>Loading…</BoardMessage>
      </BoardShell>
    );
  }
  if (isError && !nowNext) {
    return <BoardUnreachable />;
  }
  if (!currentRace) {
    return (
      <BoardShell eventName={nowNext?.eventName}>
        <BoardMessage>
          {nowNext?.nextRace ? `Next up: ${nowNext.nextRace.label}` : 'No race on track'}
        </BoardMessage>
      </BoardShell>
    );
  }
  return (
    <BoardShell eventName={nowNext?.eventName}>
      <div className="flex flex-wrap items-baseline justify-between gap-4">
        <h1 className="text-4xl font-bold lg:text-6xl">{currentRace.label}</h1>
        <span
          className={
            currentRace.status === 'STOPPED'
              ? 'rounded bg-red-600 px-4 py-1 text-2xl font-bold lg:text-3xl'
              : 'rounded bg-green-600 px-4 py-1 text-2xl font-bold lg:text-3xl'
          }
        >
          {currentRace.status === 'STOPPED' ? 'Stopped' : 'Racing'}
        </span>
      </div>
      {rows.length > 0 ? <BoardLiveTable rows={rows} nowMs={showBars && status === 'RUNNING' ? nowMs : undefined} closeMs={closeMs} /> : <BoardMessage>Waiting for the first lap…</BoardMessage>}
      {series.length > 0 && (
        <div className="mx-auto w-full max-w-6xl">
          <PositionChart series={series} />
        </div>
      )}
    </BoardShell>
  );
}
