// Anonymous spectator board for a venue TV (L12): the race on track with live
// timing, what's next, and the last finished race's results when nothing is running.
// Polls the public board endpoints and, while a race is on track, follows it live (useBoardRace).
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getResultsBoard } from '@/lib/boardsApi';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardLiveTable } from './BoardLiveTable';
import { BoardResultsTable } from './BoardResultsTable';
import { BOARD_POLL_MS, parseEventParam } from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

export default function NowNextBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { nowNext, isPending, isError, currentRace, rows } = useBoardRace(eventId);
  const nextRace = nowNext?.nextRace ?? null;
  const lastCompletedRace = nowNext?.lastCompletedRace ?? null;

  const { data: results } = useQuery({
    queryKey: publicQueryKeys.boards.results(eventId),
    queryFn: () => getResultsBoard(eventId),
    enabled: !currentRace && lastCompletedRace !== null,
    refetchInterval: BOARD_POLL_MS,
  });

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

  if (currentRace) {
    return (
      <BoardShell eventName={nowNext?.eventName} eventId={nowNext?.eventId} showQr>
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
        {rows.length > 0 ? (
          <BoardLiveTable rows={rows} />
        ) : (
          <BoardMessage>Waiting for the first lap…</BoardMessage>
        )}
        {nextRace && (
          <div className="mt-auto border-t border-slate-800 pt-6">
            <h2 className="text-xl text-slate-400 lg:text-2xl">Next up</h2>
            <p className="text-3xl font-semibold lg:text-4xl">{nextRace.label}</p>
          </div>
        )}
      </BoardShell>
    );
  }

  if (!nextRace && !lastCompletedRace) {
    return (
      <BoardShell eventName={nowNext?.eventName}>
        <BoardMessage>No race data yet.</BoardMessage>
      </BoardShell>
    );
  }

  return (
    <BoardShell eventName={nowNext?.eventName} eventId={nowNext?.eventId} showQr>
      {nextRace && (
        <div>
          <h2 className="text-xl text-slate-400 lg:text-2xl">Next up</h2>
          <h1 className="text-4xl font-bold lg:text-6xl">{nextRace.label}</h1>
        </div>
      )}
      {lastCompletedRace && (
        <div className={nextRace ? 'border-t border-slate-800 pt-6' : ''}>
          <h2 className="mb-4 text-2xl text-slate-400 lg:text-3xl">
            Last finished: {lastCompletedRace.label}
          </h2>
          {results?.race?.raceId === lastCompletedRace.raceId ? (
            <BoardResultsTable results={results.results} />
          ) : (
            <p className="text-2xl text-slate-400">Loading results…</p>
          )}
        </div>
      )}
    </BoardShell>
  );
}
