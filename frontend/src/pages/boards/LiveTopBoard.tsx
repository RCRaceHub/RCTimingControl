// Anonymous spectator board: the top of the practice or qualifying table for the class on track, with the running heat counted
// from its live positions (#480). While a heat runs the order is a projection; once it ends the settled order stays
// up, with who moved, until the next race starts.
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getLiveQualifying } from '@/lib/boardsApi';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { LiveTopTable } from './LiveTopTable';
import { parseEventParam, parseTopParam } from './boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** The table changes with every lap, so it polls faster than the other boards. */
const LIVE_TOP_POLL_MS = 3000;

export default function LiveTopBoard() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const top = parseTopParam(searchParams.get('top'));

  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.liveQualifying(eventId),
    queryFn: () => getLiveQualifying(eventId),
    refetchInterval: LIVE_TOP_POLL_MS,
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

  if (!data || data.phase === 'NONE' || data.rows.length === 0) {
    return (
      <BoardShell eventName={data?.eventName}>
        <BoardMessage>No qualifying table yet.</BoardMessage>
      </BoardShell>
    );
  }

  const live = data.phase === 'LIVE';
  return (
    <BoardShell eventName={data.eventName} eventId={data.eventId} showQr>
      <div className="flex flex-wrap items-baseline justify-between gap-4">
        <h1 className="text-4xl font-bold lg:text-6xl">
          {data.className} {data.roundType === 'PRACTICE' ? 'practice' : 'qualifying'}
        </h1>
        <p className="text-2xl text-slate-400 lg:text-3xl">
          {live ? `If ${data.raceLabel} ended now` : `After ${data.raceLabel}`}
        </p>
      </div>
      <LiveTopTable data={data} top={top} />
    </BoardShell>
  );
}
