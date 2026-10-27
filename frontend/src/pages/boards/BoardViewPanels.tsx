// The panels a saved board view is built from (#481). Each draws one thing from the public board endpoints, so a view
// is only a layout: the same data feeds the stand-alone boards.
import { useQuery } from '@tanstack/react-query';
import { getLiveQualifying, getResultsBoard, getTicker, type NowNextDto, type RaceMediaDto } from '@/lib/boardsApi';
import type { BoardPanel } from '@/lib/boardViewsApi';
import { ClassSwatch } from '@/components/ClassSwatch';
import type { LapBarClock } from '@/components/LapProgressBar';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { CarThumb } from '@/components/CarThumb';
import { BoardLiveTable, type PredictedClock } from './BoardLiveTable';
import { BoardResultsTable } from './BoardResultsTable';
import { LiveTopTable } from './LiveTopTable';
import { NameList, NextRacePanel } from './NextRacePanel';
import { BOARD_POLL_MS } from './boardFormat';

/** What the panels share: the race on track, followed live once for the whole page. */
export type PanelContext = {
  eventId: number | null;
  nowNext: NowNextDto | undefined;
  rows: LiveTimingRowDto[];
  barClock: LapBarClock | undefined;
  closeMs: number;
  showArrows: boolean;
  predicted: PredictedClock | undefined;
  /** Headshots, car photos and driver sponsors of the race on track, by entry id (#520). */
  media: Map<number, RaceMediaDto>;
};

function PanelTitle({ children }: { children: string }) {
  return <h2 className="mb-3 text-xl text-slate-400 lg:text-2xl">{children}</h2>;
}

function Empty({ children }: { children: string }) {
  return <p className="text-2xl text-slate-400">{children}</p>;
}

function LiveTimingPanel({ ctx }: { ctx: PanelContext }) {
  const race = ctx.nowNext?.currentRace ?? null;
  if (!race) {
    return <Empty>{ctx.nowNext?.nextRace ? `Next up: ${ctx.nowNext.nextRace.label}` : 'No race on track'}</Empty>;
  }
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-3xl font-bold lg:text-4xl">
          <ClassSwatch colour={race.classColour} className="mr-3" />
          {race.label}
        </h2>
        <span
          className={
            race.status === 'STOPPED'
              ? 'rounded bg-red-600 px-3 py-1 text-xl font-bold'
              : 'rounded bg-green-600 px-3 py-1 text-xl font-bold'
          }
        >
          {race.status === 'STOPPED' ? 'Stopped' : 'Racing'}
        </span>
      </div>
      {ctx.rows.length > 0 ? (
        <BoardLiveTable
          rows={ctx.rows}
          media={ctx.media}
          barClock={race.status === 'RUNNING' ? ctx.barClock : undefined}
          closeMs={ctx.closeMs}
          showArrows={ctx.showArrows && race.finalLetter !== null}
          predicted={ctx.predicted}
        />
      ) : (
        <Empty>Waiting for the first lap…</Empty>
      )}
    </div>
  );
}

function LiveTopPanel({ ctx, top }: { ctx: PanelContext; top: number }) {
  const { data } = useQuery({
    queryKey: publicQueryKeys.boards.liveQualifying(ctx.eventId),
    queryFn: () => getLiveQualifying(ctx.eventId),
    refetchInterval: 3000,
  });
  if (!data || data.phase === 'NONE' || data.rows.length === 0) {
    return <Empty>No qualifying table yet.</Empty>;
  }
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-3xl font-bold lg:text-4xl">
          {data.className} {data.roundType === 'PRACTICE' ? 'practice' : 'qualifying'}
        </h2>
        <p className="text-xl text-slate-400">
          {data.phase === 'LIVE' ? `If ${data.raceLabel} ended now` : `After ${data.raceLabel}`}
        </p>
      </div>
      <LiveTopTable data={data} top={top} />
    </div>
  );
}

function MarshalsPanel({ ctx }: { ctx: PanelContext }) {
  const tickerEventId = ctx.eventId ?? ctx.nowNext?.eventId ?? null;
  const { data: ticker } = useQuery({
    queryKey: publicQueryKeys.boards.ticker(tickerEventId),
    queryFn: () => getTicker(tickerEventId),
    refetchInterval: BOARD_POLL_MS,
  });
  const names = ticker?.currentMarshals ?? [];
  if (!ctx.nowNext?.currentRace || names.length === 0) {
    return (
      <div>
        <PanelTitle>Marshals</PanelTitle>
        <Empty>No marshals listed for this race.</Empty>
      </div>
    );
  }
  return <NameList title="Marshals" names={names} testId="view-marshals" />;
}

function LastResultPanel({ ctx }: { ctx: PanelContext }) {
  const last = ctx.nowNext?.lastCompletedRace ?? null;
  const { data } = useQuery({
    queryKey: publicQueryKeys.boards.results(ctx.eventId),
    queryFn: () => getResultsBoard(ctx.eventId),
    enabled: last !== null,
    refetchInterval: BOARD_POLL_MS,
  });
  if (!last) return <Empty>No result yet.</Empty>;
  return (
    <div>
      <PanelTitle>{`Last finished: ${last.label}`}</PanelTitle>
      {data?.race?.raceId === last.raceId ? <BoardResultsTable results={data.results} /> : <Empty>Loading results…</Empty>}
    </div>
  );
}

/**
 * The drivers of the race on track in race order, each with a headshot, car number and their own sponsors, for the
 * commentator (#522). A driver without a picture gets a plain card; one whose name is hidden has no picture or sponsors
 * (the server leaves them out) and shows as the name the timing already gives.
 */
function DriverCardsPanel({ ctx }: { ctx: PanelContext }) {
  const race = ctx.nowNext?.currentRace ?? null;
  if (!race || ctx.rows.length === 0) {
    return <Empty>No race on track</Empty>;
  }
  return (
    <div>
      <PanelTitle>{`Drivers: ${race.label}`}</PanelTitle>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3" data-testid="view-driver-cards">
        {ctx.rows.map((row) => {
          const media = ctx.media.get(row.entryId);
          return (
            <li key={row.entryId} className="flex items-center gap-4 rounded-lg bg-slate-900 p-3">
              {media?.headshotUrl ? (
                <img src={media.headshotUrl} alt="" loading="lazy" className="h-20 w-20 shrink-0 rounded-full object-cover" />
              ) : (
                <div className="h-20 w-20 shrink-0 rounded-full bg-slate-800" aria-hidden />
              )}
              <div className="min-w-0">
                <p className="truncate text-2xl font-bold">
                  <span className="mr-2 tabular-nums text-slate-400">{row.position}.</span>
                  {row.carNumber != null && <span className="mr-2 tabular-nums text-slate-400">#{row.carNumber}</span>}
                  {row.driverName}
                </p>
                {media && media.sponsors.length > 0 && (
                  <p className="truncate text-lg text-slate-400">{media.sponsors.join(' · ')}</p>
                )}
                <CarThumb url={media?.carPhotoUrl} className="mt-1 h-10 w-16" />
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** One panel of a view. */
export function BoardViewPanel({ panel, ctx }: { panel: BoardPanel; ctx: PanelContext }) {
  switch (panel.type) {
    case 'LIVE_TIMING':
      return <LiveTimingPanel ctx={ctx} />;
    case 'LIVE_TOP':
      return <LiveTopPanel ctx={ctx} top={panel.top ?? 10} />;
    case 'NEXT_RACE':
      return <NextRacePanel nowNext={ctx.nowNext} eventId={ctx.eventId} />;
    case 'MARSHALS':
      return <MarshalsPanel ctx={ctx} />;
    case 'LAST_RESULT':
      return <LastResultPanel ctx={ctx} />;
    case 'DRIVER_CARDS':
      return <DriverCardsPanel ctx={ctx} />;
  }
}
