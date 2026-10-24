// A saved board view (#481): the panels an official picked, laid out in columns, at /boards/view/{slug}. Anonymous like
// the other boards. The race on track is followed once here and shared by the panels; options on the address are the
// live race board's (bars=hide, close=SECONDS) and event=ID.
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getBoardView } from '@/lib/boardViewsApi';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardViewPanel } from './BoardViewPanels';
import { parseEventParam, parseLiveRaceOptions } from './boardFormat';
import { useBoardRace } from './useBoardRace';

export default function BoardViewPage() {
  const { slug = '' } = useParams();
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const { showBars, closeMs } = parseLiveRaceOptions(searchParams);

  const { data: view, isPending, isError, error } = useQuery({
    queryKey: publicQueryKeys.boards.view(slug),
    queryFn: () => getBoardView(slug),
    refetchInterval: 30_000,
    retry: false,
  });
  const { nowNext, currentRace, rows } = useBoardRace(eventId);
  const barClock = useLapBarClock(rows, currentRace?.status);

  if (isPending) {
    return (
      <BoardShell>
        <BoardMessage>Loading…</BoardMessage>
      </BoardShell>
    );
  }
  if (isError && !view) {
    const notFound = (error as { response?: { status?: number } })?.response?.status === 404;
    return notFound ? (
      <BoardShell>
        <BoardMessage>{`There is no board view called "${slug}".`}</BoardMessage>
      </BoardShell>
    ) : (
      <BoardUnreachable />
    );
  }

  const ctx = { eventId, nowNext, rows, barClock: showBars ? barClock : undefined, closeMs };
  return (
    <BoardShell eventName={nowNext?.eventName}>
      <div className="grid gap-8" style={{ gridTemplateColumns: `repeat(${view.columns}, minmax(0, 1fr))` }}>
        {view.panels.map((panel, i) => (
          <section key={`${i}-${panel.type}`} style={{ gridColumn: `span ${panel.span}` }} className="min-w-0">
            <BoardViewPanel panel={panel} ctx={ctx} />
          </section>
        ))}
      </div>
    </BoardShell>
  );
}
