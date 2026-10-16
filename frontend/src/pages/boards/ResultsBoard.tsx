// Anonymous spectator board showing the last finished race's results (L12).
// Polls the public board endpoint so a newly finished race appears on its own.
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getResultsBoard } from '@/lib/boardsApi';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardResultsTable } from './BoardResultsTable';
import { BOARD_POLL_MS, parseEventParam } from './boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

export default function ResultsBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));

  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.results(eventId),
    queryFn: () => getResultsBoard(eventId),
    refetchInterval: BOARD_POLL_MS,
  });

  if (isPending) {
    return (
      <BoardShell>
        <BoardMessage>Loading…</BoardMessage>
      </BoardShell>
    );
  }

  if (isError && !data) {
    return <BoardUnreachable />;
  }

  if (!data?.race) {
    return (
      <BoardShell eventName={data?.eventName}>
        <BoardMessage>No results yet.</BoardMessage>
      </BoardShell>
    );
  }

  return (
    <BoardShell eventName={data.eventName} eventId={data.eventId} showQr>
      <h1 className="text-4xl font-bold lg:text-6xl">{data.race.label}</h1>
      <BoardResultsTable results={data.results} />
    </BoardShell>
  );
}
