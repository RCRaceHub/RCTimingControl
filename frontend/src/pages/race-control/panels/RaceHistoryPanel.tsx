import { useQuery } from '@tanstack/react-query';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getRaceHistory } from '@/lib/raceControlApi';
import type { RaceHistoryKind, RunOrderItemDto } from '@/lib/raceControlApi';
import { formatTime } from '@/lib/dates';
import { QueryError } from '@/components/QueryError';

type RaceStatus = RunOrderItemDto['status'];

const KIND_LABEL: Record<RaceHistoryKind, string> = {
  LIFECYCLE: 'Race',
  PENALTY: 'Penalty',
  INCIDENT: 'Incident',
  MARSHAL_LAP: 'Marshal lap',
  MARSHAL_ABSENCE: 'Marshalling',
  MARSHAL_PENALTY: 'Marshalling penalty',
  JUMP_START: 'Jump start',
  TRANSPONDER_LINK: 'Transponder',
  OTHER: 'Other',
};

const LIVE_STATUSES: RaceStatus[] = ['GRID', 'RUNNING', 'STOPPED'];

/** What happened in the race, oldest first: lifecycle, penalties, incidents, marshal laps and links (#140). */
export function RaceHistoryPanel({ raceId, status }: { raceId: number; status: RaceStatus }) {
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: raceControlQueryKeys.raceHistory(raceId),
    queryFn: () => getRaceHistory(raceId),
    // New things keep happening until the race is over
    refetchInterval: LIVE_STATUSES.includes(status) ? 5000 : false,
  });

  return (
    <section aria-label="Race history" className="flex flex-col gap-2">
      <h2 className="text-sm font-semibold">What happened</h2>
      {isPending && (
        <p role="status" className="text-sm text-muted-foreground">Loading…</p>
      )}
      {isError && (
        <QueryError message="Could not load the race history." onRetry={refetch} />
      )}
      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground">Nothing has been recorded for this race yet.</p>
      )}
      {data && data.length > 0 && (
        <ul className="divide-y rounded-md border text-sm">
          {data.map((item, i) => (
            <li key={`${item.at}-${i}`} className="flex gap-3 px-3 py-2">
              <time className="w-24 shrink-0 whitespace-nowrap tabular-nums text-muted-foreground" dateTime={item.at}>
                {formatTime(item.at, true)}
              </time>
              <span className="w-32 shrink-0 font-medium">{KIND_LABEL[item.kind] ?? item.kind}</span>
              <span className="flex-1">
                {item.driver && <strong>{item.driver}: </strong>}
                {item.summary}
              </span>
              {item.actor && <span className="max-w-40 shrink-0 truncate text-muted-foreground">{item.actor}</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
