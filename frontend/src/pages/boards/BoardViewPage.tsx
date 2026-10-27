// A saved board view (#481): the panels an official picked, laid out in columns, at /boards/view/{slug}. Anonymous like
// the other boards. The race on track is followed once here and shared by the panels; options on the address are the
// live race board's (bars=hide, arrows=hide, predict=hide, close=SECONDS) and event=ID.
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getRaceClock } from '@/lib/boardsApi';
import { useRemainingAt } from '@/hooks/race-control/useRemainingAt';
import { getBoardView } from '@/lib/boardViewsApi';
import { useRaceMedia } from '@/hooks/useRaceMedia';
import { useBoardSettings } from '@/hooks/useBoardSettings';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { BoardShell, BoardMessage, BoardUnreachable } from './BoardShell';
import { BoardViewPanel } from './BoardViewPanels';
import { BOARD_POLL_MS, parseEventParam, parseLiveRaceOptions } from './boardFormat';
import { useBoardRace } from './useBoardRace';

export default function BoardViewPage() {
  const { slug = '' } = useParams();
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const settings = useBoardSettings();
  const { showBars, showArrows, showPredicted, closeMs, showLegend } = parseLiveRaceOptions(searchParams, settings.closeGapMs);

  const { data: view, isPending, isError, error } = useQuery({
    queryKey: publicQueryKeys.boards.view(slug),
    queryFn: () => getBoardView(slug),
    refetchInterval: 30_000,
    retry: false,
  });
  const { nowNext, currentRace, rows } = useBoardRace(eventId);
  const barClock = useLapBarClock(rows, currentRace?.status, settings.lateMarginMs);
  const raceId = currentRace?.raceId ?? null;
  const media = useRaceMedia(raceId);
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

  const ctx = { eventId, nowNext, rows, barClock: showBars ? barClock : undefined,
    closeMs,
    showLegend,
    showArrows,
    media,
    predicted: showPredicted && clock?.remainingMs != null ? { clock: barClock, remainingAt } : undefined,
  };
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
