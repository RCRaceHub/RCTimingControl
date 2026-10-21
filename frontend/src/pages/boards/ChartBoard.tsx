// A venue TV screen of its own for the live position chart (#410): who holds which place, lap by lap, for the race
// on track. Anonymous like the other boards, and follows the race live through useBoardRace.
import { useSearchParams } from 'react-router-dom';
import { PositionChart } from '@/components/print/PositionChart';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { parseEventParam } from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { useLivePositionSeries } from './useLivePositionSeries';

export default function ChartBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { nowNext, isPending, isError, currentRace, raceId, rows } = useBoardRace(eventId);
  const series = useLivePositionSeries(raceId, rows);

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
      <h1 className="text-4xl font-bold lg:text-6xl">{currentRace.label}</h1>
      {series.length > 0 ? (
        <div className="mx-auto w-full max-w-6xl">
          <PositionChart series={series} />
        </div>
      ) : (
        <BoardMessage>Waiting for the first lap…</BoardMessage>
      )}
    </BoardShell>
  );
}
