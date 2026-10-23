// Anonymous spectator board for a venue TV (L12): the line-up of the race on track, who is on the grid next and who
// marshals it, and the last finished race's results when nothing is running. The live timing table and position
// chart are on the Live Race screen (LiveRaceBoard). Polls the public board endpoints and, while a race is on
// track, follows its running order live (useBoardRace).
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getResultsBoard, getTicker, type AutoRunBoardDto } from '@/lib/boardsApi';
import { formatCountdown, useCountdown } from '@/lib/useCountdown';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardResultsTable } from './BoardResultsTable';
import { BOARD_POLL_MS, parseEventParam } from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/**
 * Auto run's countdown to the next start, large enough to read from the pits. While it is holding (before the
 * finals, or for a problem) it says the start is waiting for race control instead of counting.
 */
function NextStart({ autoRun }: { autoRun: AutoRunBoardDto | null | undefined }) {
  const left = useCountdown(autoRun?.phase === 'WAITING' ? autoRun.startAt : null, autoRun?.serverTime);
  if (!autoRun) return null;
  if (autoRun.phase === 'HELD') {
    return (
      <p className="text-3xl font-semibold text-amber-400 lg:text-5xl" data-testid="board-next-start-held">
        {autoRun.heldReason === 'FINALS' ? 'Waiting for the finals to be released' : 'Waiting for race control'}
      </p>
    );
  }
  if (left == null) return null;
  return (
    <p className="text-3xl text-slate-300 lg:text-5xl">
      Starts in{' '}
      <span className="font-mono text-6xl font-bold tabular-nums text-white lg:text-8xl" data-testid="board-next-start">
        {formatCountdown(left)}
      </span>
    </p>
  );
}

/** A names list under a heading, or nothing when there is no one to list. */
function NameList({
  title,
  names,
  testId,
  pendingText,
}: {
  title: string;
  names: string[];
  testId: string;
  pendingText?: string;
}) {
  if (pendingText) {
    return (
      <div data-testid={testId}>
        <h3 className="text-lg text-slate-400 lg:text-2xl">{title}</h3>
        <p className="mt-1 text-2xl text-slate-400 lg:text-4xl">{pendingText}</p>
      </div>
    );
  }
  if (names.length === 0) return null;
  return (
    <div data-testid={testId}>
      <h3 className="text-lg text-slate-400 lg:text-2xl">{title}</h3>
      <ul className="mt-1 flex flex-wrap gap-x-8 gap-y-1 text-2xl font-semibold lg:text-4xl">
        {names.map((name, i) => (
          <li key={`${i}-${name}`}>{name}</li>
        ))}
      </ul>
    </div>
  );
}

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

  // Pinned to the event this board shows, so the names can't come from a different event than the race above them
  const tickerEventId = eventId ?? nowNext?.eventId ?? null;
  const { data: ticker } = useQuery({
    queryKey: publicQueryKeys.boards.ticker(tickerEventId),
    queryFn: () => getTicker(tickerEventId),
    enabled: nextRace !== null,
    refetchInterval: BOARD_POLL_MS,
  });
  // The ticker polls on its own timer, so ignore its names until it is talking about the race shown as next
  const tickerIsCurrent = nextRace !== null && ticker?.nextRace === nextRace.label;
  const nextDrivers = tickerIsCurrent ? ticker.nextDrivers : [];
  const nextMarshals = tickerIsCurrent ? ticker.marshals : [];
  const marshalsPendingText =
    tickerIsCurrent && ticker.marshalsPending ? 'To be confirmed when the final below finishes' : undefined;

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
