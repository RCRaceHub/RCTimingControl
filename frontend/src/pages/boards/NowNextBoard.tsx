// Anonymous spectator board for a venue TV (L12): the line-up of the race on track, who is on the grid next and who
// marshals it, and the last finished race's results when nothing is running. The live timing table and position
// chart are on the Live Race screen (LiveRaceBoard). Polls the public board endpoints and, while a race is on
// track, follows its running order live (useBoardRace).
import { useSearchParams } from 'react-router-dom';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardResultsTable } from './BoardResultsTable';
import { BOARD_POLL_MS, parseEventParam } from './boardFormat';
import { NameList, NextStart } from './NextRacePanel';
import { useNextRaceNames } from './useNextRaceNames';
import { useBoardRace } from './useBoardRace';
import { useResultsBoardQuery } from '@/hooks/useBoardQueries';

export default function NowNextBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { nowNext, isPending, isError, currentRace, rows } = useBoardRace(eventId);
  const nextRace = nowNext?.nextRace ?? null;
  const lastCompletedRace = nowNext?.lastCompletedRace ?? null;

  const { data: results } = useResultsBoardQuery(eventId, {
    intervalMs: BOARD_POLL_MS,
    enabled: !currentRace && lastCompletedRace !== null,
  });

  const { nextDrivers, nextMarshals, marshalsPendingText } = useNextRaceNames(nowNext, eventId);

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
          <ol className="grid gap-x-12 gap-y-2 text-2xl sm:grid-cols-2 lg:text-4xl" aria-label="Line-up">
            {rows.map((row) => (
              <li key={row.entryId} className="flex gap-4 border-b border-slate-800 py-2">
                <span className="w-12 font-bold tabular-nums">{row.position}</span>
                <span className="w-16 tabular-nums text-slate-400">{row.carNumber ?? '—'}</span>
                <span className="font-semibold">{row.driverName}</span>
              </li>
            ))}
          </ol>
        ) : (
          <BoardMessage>Waiting for the line-up…</BoardMessage>
        )}
        {nextRace && (
          <div className="mt-auto border-t border-slate-800 pt-6">
            <h2 className="text-xl text-slate-400 lg:text-2xl">Next up</h2>
            <p className="text-3xl font-semibold lg:text-4xl">{nextRace.label}</p>
            <NextStart autoRun={nowNext?.autoRun} />
            <div className="mt-4 flex flex-col gap-4">
              <NameList title="Next drivers" names={nextDrivers} testId="board-next-drivers" />
              <NameList title="Next marshals" names={nextMarshals} testId="board-next-marshals" pendingText={marshalsPendingText} />
            </div>
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
          <NextStart autoRun={nowNext?.autoRun} />
          <div className="mt-4 flex flex-col gap-4">
            <NameList title="Next drivers" names={nextDrivers} testId="board-next-drivers" />
            <NameList title="Next marshals" names={nextMarshals} testId="board-next-marshals" pendingText={marshalsPendingText} />
          </div>
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
