// The Live Race screen for a venue TV: the race on track with its live timing table and the position chart (#410),
// who holds which place lap by lap. Anonymous like the other boards, and follows the race live through useBoardRace.
// Cars running close together show as a battles strip under the table (#505).
// Now and next keeps to the line-up and what is coming, so the timing and the chart live here.
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getRaceClock } from '@/lib/boardsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { useRemainingAt } from '@/hooks/race-control/useRemainingAt';
import { PositionChart } from '@/components/print/PositionChart';
import { ClassSwatch } from '@/components/ClassSwatch';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import { useBattles } from '@/hooks/race-control/useBattles';
import { BattleStrip } from '@/components/BattleStrip';
import { BoardLiveTable } from './BoardLiveTable';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BOARD_POLL_MS, parseEventParam, parseLiveRaceOptions } from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { useLivePositionSeries } from './useLivePositionSeries';

export default function LiveRaceBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { nowNext, isPending, isError, currentRace, raceId, rows } = useBoardRace(eventId);
  const series = useLivePositionSeries(raceId, rows);

  const { showBars, showArrows, showPredicted, closeMs, showBattles } = parseLiveRaceOptions(searchParams);
  const battleGroups = useBattles(showBattles ? raceId : null);
  const barClock = useLapBarClock(rows, currentRace?.status);
  const { data: clock, dataUpdatedAt: clockReadAt } = useQuery({
    queryKey: publicQueryKeys.boards.clock(raceId),
    queryFn: () => getRaceClock(raceId!),
    enabled: raceId !== null && showPredicted,
    refetchInterval: BOARD_POLL_MS,
  });
  const remainingAt = useRemainingAt(clock, clockReadAt);

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
        <h1 className="text-4xl font-bold lg:text-6xl">
          <ClassSwatch colour={currentRace.classColour} className="mr-3" />
          {currentRace.label}
        </h1>
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
      {rows.length > 0 ? (
        <BoardLiveTable
          rows={rows}
          barClock={showBars && currentRace.status === 'RUNNING' ? barClock : undefined}
          closeMs={closeMs}
          showArrows={showArrows && currentRace.finalLetter !== null}
          predicted={showPredicted && clock?.remainingMs != null ? { clock: barClock, remainingAt } : undefined}
        />
      ) : (
        <BoardMessage>Waiting for the first lap…</BoardMessage>
      )}
      {currentRace.status === 'RUNNING' && (
        <BattleStrip groups={battleGroups} className="rounded-lg bg-slate-800/60 px-6 py-3 text-2xl lg:text-3xl" />
      )}
      {series.length > 0 && (
        <div className="mx-auto w-full max-w-6xl">
          <PositionChart series={series} />
        </div>
      )}
    </BoardShell>
  );
}
