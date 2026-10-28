// Streaming overlay for an OBS browser source (#29): the race in progress on a transparent page, with its
// running order, laps, last lap and race clock. Anonymous, like the other boards. It follows the race's STOMP
// timing and state topics (useBoardRace), and shows a small idle panel while no race is on track, so a blank page
// never looks like a broken overlay.
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { cn } from '@/lib/utils';
import type { SponsorDto } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import {
  BOARD_POLL_MS,
  fmtClock,
  heatBestLap,
  overlayDesignFromTheme,
  overlayQuery,
  parseEventParam,
  parseOverlayOptions,
  roundBestLap,
  tickerSegments,
  type TickerItem,
} from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { useHighContrast } from '@/hooks/useHighContrast';
import { TqPace } from '@/components/TqPace';
import { OverlayTower, TOWER_WIDTH_CLASS, type ProjectedQualifying } from './OverlayTower';
import { useGapView } from '@/hooks/useGapView';
import { useRemainingAt } from '@/hooks/race-control/useRemainingAt';
import { useLivePositionSeries } from './useLivePositionSeries';
import { PositionChart } from '@/components/print/PositionChart';
import { TimingLegend, type LegendKind } from '@/components/TimingLegend';
import { useLapFlash } from '@/hooks/race-control/useLapFlash';
import { FinishedFlag } from '@/components/FinishedFlag';
import { LapProgressBar } from '@/components/LapProgressBar';
import { OnPaceIcon } from '@/components/OnPaceIcon';
import { PositionArrow } from '@/components/PositionArrow';
import { RowStateIcon } from '@/components/StateIcon';
import { isClose } from '@/lib/lapProgress';
import { BattleStrip } from '@/components/BattleStrip';
import { useBattles } from '@/hooks/race-control/useBattles';
import { useBoardSettings } from '@/hooks/useBoardSettings';
import { useLapBarClock } from '@/hooks/race-control/useLapBarClock';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { useBoardSponsorsQuery, useLiveQualifyingQuery, useOverlayThemeQuery, useRaceClockQuery, useRaceHighlightsQuery, useRoundFastestQuery, useTickerQuery } from '@/hooks/useBoardQueries';

const THEMES = {
  dark: {
    panel: 'dark bg-slate-950/80 text-white',
    muted: 'text-slate-300',
    row: 'border-white/10',
    position: 'bg-white/15',
  },
  light: {
    panel: 'bg-white/85 text-slate-950',
    muted: 'text-slate-600',
    row: 'border-slate-900/10',
    position: 'bg-slate-900/10',
  },
};

/** Clears the page background while the overlay is shown, so OBS sees through everything but the panel. */
function useTransparentPage() {
  useEffect(() => {
    const html = document.documentElement;
    const body = document.body;
    const before = [html.style.background, body.style.background];
    html.style.background = 'transparent';
    body.style.background = 'transparent';
    return () => {
      html.style.background = before[0];
      body.style.background = before[1];
    };
  }, []);
}

/** Ticks once a second so a running clock counts between updates from the app. */
function useNow(active: boolean) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
  return now;
}

/** How often the projected qualifying places are re-read, like the Live top screen. */
const LIVE_QUALIFYING_POLL_MS = 3000;

/** How long each ticker stat stays up before the next one rolls in. */
const TICKER_MS = 5000;

/** Rolls through the heat's fastest lap and the round's fastest lap, one stat at a time. */
function StatsTicker({
  rows,
  raceId,
  mutedClass,
  rowClass,
}: {
  rows: LiveTimingRowDto[];
  raceId: number;
  mutedClass: string;
  rowClass: string;
}) {
  const { data: round } = useRoundFastestQuery(raceId, { intervalMs: BOARD_POLL_MS * 6 });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), TICKER_MS);
    return () => clearInterval(timer);
  }, []);

  const heatBest = heatBestLap(rows);
  // The round's fastest includes this heat, which has no snapshot until it finishes
  const roundBest = roundBestLap(heatBest, round);

  const stats: Array<[string, string]> = [];
  if (heatBest) stats.push(['Fastest lap', `${fmtMs(heatBest.lapMs)} · ${heatBest.driverName}`]);
  if (roundBest) stats.push(['Round fastest', `${fmtMs(roundBest.lapMs)} · ${roundBest.driverName}`]);
  if (stats.length === 0) return null;

  const [label, value] = stats[tick % stats.length];
  return (
    <div
      className={cn('flex items-baseline gap-2 border-t px-4 py-1.5 text-sm [contain:inline-size]', rowClass)}
      aria-label="Race stats"
      aria-live="off"
    >
      <span className={cn('text-xs uppercase tracking-wide', mutedClass)}>{label}</span>
      <span className="min-w-0 flex-1 truncate font-semibold tabular-nums">{value}</span>
    </div>
  );
}

/** Seconds a ticker takes per character to cross the screen: slow enough to read, long text just takes longer. */
const TICKER_SECONDS_PER_CHAR = 0.22;
const TICKER_MIN_SECONDS = 24;
/** What the wide gap and separator between two ticker items count for when timing the scroll, in characters. */
const TICKER_GAP_CHARS = 8;

/**
 * A strip along the foot of the page that scrolls the next race, who is on its grid and marshalling, the class
 * top qualifiers and the fastest lap of the round, as far as the page address switches each on. It stays up
 * between races, when "next" is what people want. Renders nothing when there is nothing to say.
 */
function OverlayTicker({
  eventId,
  raceId,
  rows,
  items,
  panelClass,
  bottom,
  separatorImage,
}: {
  eventId: number | null;
  raceId: number | null;
  rows: LiveTimingRowDto[];
  items: Set<TickerItem>;
  panelClass: string;
  /** How far the strip sits above the foot of the page, so it clears the sponsor bar when that is showing. */
  bottom: string;
  /** The address of an image between items instead of the diamond; null keeps the diamond. */
  separatorImage: string | null;
}) {
  const { data: ticker } = useTickerQuery(eventId, {
    intervalMs: BOARD_POLL_MS * 4,
    enabled: (['next', 'drivers', 'marshals', 'tq'] as const).some(item => items.has(item)),
  });
  const { data: round } = useRoundFastestQuery(raceId, { intervalMs: BOARD_POLL_MS * 6, enabled: items.has('fastest') });
  const { data: highlights } = useRaceHighlightsQuery(raceId, {
    intervalMs: BOARD_POLL_MS * 4,
    enabled: items.has('gain') || items.has('loss') || items.has('pace'),
  });
  const segments = tickerSegments(items, ticker, roundBestLap(heatBestLap(rows), round), highlights);
  // After the first pass, new text waits for the line to finish crossing the screen, so a change never makes it
  // jump mid-scroll
  const [shown, setShown] = useState<string[] | null>(null);
  if (segments.length === 0) return null;
  const display = shown ?? segments;
  const length = display.reduce((sum, segment) => sum + segment.length + TICKER_GAP_CHARS, 0);
  return (
    <div
      className={cn('fixed inset-x-0 overflow-hidden py-3 font-sans text-3xl font-semibold', panelClass)}
      style={{ bottom }}
      aria-label="Ticker"
    >
      <span
        className="ticker-scroll"
        style={{ animationDuration: `${Math.max(TICKER_MIN_SECONDS, length * TICKER_SECONDS_PER_CHAR)}s` }}
        onAnimationIteration={() => setShown(segments)}
      >
        {display.map((segment, index) => (
          <span key={index}>
            {index > 0 && (
              <span className="mx-16 inline-flex items-center align-middle opacity-50" aria-hidden="true">
                {separatorImage ? <img src={separatorImage} alt="" className="h-9 w-auto max-w-[8rem] object-contain" /> : '◆'}
              </span>
            )}
            {segment}
          </span>
        ))}
      </span>
    </div>
  );
}

/** Most sponsors shown at once; with more, the bar shows them in turns. */
const SPONSORS_PER_PAGE = 6;
const SPONSOR_PAGE_MS = 8000;
/** The height of the sponsor bar, and of the ticker above it, which the strips and the chart stack on. */
const SPONSOR_BAR_REM = 6;
const TICKER_REM = 5;

/**
 * A bar along the foot of the page with the event's sponsors: logos side by side, or the name where a sponsor has no
 * logo. More than {@link SPONSORS_PER_PAGE} take turns. Renders nothing when the event has no sponsors.
 */
function SponsorBar({ sponsors, panelClass }: { sponsors: SponsorDto[]; panelClass: string }) {
  const [page, setPage] = useState(0);
  const pages = Math.ceil(sponsors.length / SPONSORS_PER_PAGE);
  useEffect(() => {
    if (pages < 2) return;
    const timer = setInterval(() => setPage(p => p + 1), SPONSOR_PAGE_MS);
    return () => clearInterval(timer);
  }, [pages]);
  if (sponsors.length === 0) return null;
  const shown = sponsors.slice((page % pages) * SPONSORS_PER_PAGE, (page % pages + 1) * SPONSORS_PER_PAGE);
  return (
    <div
      className={cn('fixed inset-x-0 bottom-0 flex items-center justify-center gap-12 px-8 font-sans', panelClass)}
      style={{ height: `${SPONSOR_BAR_REM}rem` }}
      aria-label="Sponsors"
    >
      {shown.map(sponsor =>
        sponsor.logoUrl ? (
          <img key={sponsor.id} src={sponsor.logoUrl} alt={sponsor.name} className="h-[4.5rem] w-auto max-w-[14rem] object-contain" />
        ) : (
          <span key={sponsor.id} className="text-3xl font-bold">
            {sponsor.name}
          </span>
        ),
      )}
    </div>
  );
}

/** How often an overlay opened with `?look=ID` re-reads its theme, so an edit in the designer shows on stream. */
const THEME_POLL_MS = 15000;

/**
 * The options the overlay draws with. With `?look=ID` they come from that saved theme (only `event` still comes from the
 * address, as a theme does not pick an event); while the theme loads, or if it is gone, the address's own options apply.
 */
function useOverlayParams(): URLSearchParams {
  const [searchParams] = useSearchParams();
  const lookId = parseEventParam(searchParams.get('look'));
  const { data: look } = useOverlayThemeQuery(lookId, { intervalMs: THEME_POLL_MS });
  return useMemo(() => {
    if (!look) return searchParams;
    const design = { ...overlayDesignFromTheme(look.design), eventId: parseEventParam(searchParams.get('event')) };
    return new URLSearchParams(overlayQuery(design));
  }, [look, searchParams]);
}

export default function OverlayBoard() {
  const searchParams = useOverlayParams();
  const settings = useBoardSettings();
  const {
    eventId, top, showClass, theme, showChart, tickerItems, showSponsors, layout, showQualifying, showBumpLine, showTqPace, showClassTags, gap,
    gapEverySeconds, separatorImage, showPredicted, showBars, showArrows, closeMs, showBattles, showLegend, highContrast,
  } = parseOverlayOptions(searchParams, settings.closeGapMs);
  useHighContrast(highContrast);
  const colours = THEMES[theme];
  const gapView = useGapView(gap, gapEverySeconds);
  // The laps column takes turns between laps done and predicted laps on the same period as the gap
  const lapsView = useGapView('auto', gapEverySeconds);
  useTransparentPage();

  const { nowNext, isError, currentRace: race, raceId, rows: allRows } = useBoardRace(eventId);
  const { data: clock, dataUpdatedAt: clockReadAt } = useRaceClockQuery(raceId, { intervalMs: BOARD_POLL_MS });

  const remainingAt = useRemainingAt(clock, clockReadAt);
  const battleGroups = useBattles(showBattles ? raceId : null);
  // The grid is bunched up until the field has spread out, so no battle call-outs before a lap has been counted
  const lapCounted = allRows.some(row => row.lapsCompleted >= 1);
  const barClock = useLapBarClock(allRows, race?.status, settings.lateMarginMs);
  const legendKinds: LegendKind[] = ['lap', ...(closeMs > 0 ? ['close' as const] : []), ...(showBars ? ['pace' as const] : []), ...(layout === 'tower' ? ['status' as const] : [])];
  const now = useNow(!!clock?.running);
  const rows = allRows.slice(0, top);
  // Judged on the whole field, so a driver below the cut-off still keeps their lap history
  const lapFlash = useLapFlash(allRows);
  const chartSeries = useLivePositionSeries(raceId, allRows, showChart);
  const tickerEventId = eventId ?? nowNext?.eventId ?? null;
  // Where each driver stands in the event's qualifying table if this heat ended now, refreshed with every lap
  const isQualifier = race?.roundType === 'QUALIFIER';
  const { data: liveQualifying } = useLiveQualifyingQuery(tickerEventId, {
    intervalMs: LIVE_QUALIFYING_POLL_MS,
    enabled: (showQualifying || showTqPace) && isQualifier && layout === 'tower',
  });
  const raceIdNow = race?.raceId ?? null;
  const qualifying = useMemo(() => {
    if (!showQualifying || raceIdNow === null || liveQualifying?.raceId !== raceIdNow) return undefined;
    return new Map<number, ProjectedQualifying>(
      liveQualifying.rows.map(r => [r.entryId, { position: r.position, previousPosition: r.previousPosition }]),
    );
  }, [liveQualifying, raceIdNow, showQualifying]);
  const tqRow = liveQualifying?.raceId === raceIdNow ? liveQualifying?.rows[0] : undefined;
  const { data: sponsors = [] } = useBoardSponsorsQuery(tickerEventId, { intervalMs: BOARD_POLL_MS * 12, enabled: showSponsors });
  const sponsorsShowing = showSponsors && sponsors.length > 0;
  const tickerBottom = sponsorsShowing ? `${SPONSOR_BAR_REM}rem` : '0px';
  const chartBottom = `${(sponsorsShowing ? SPONSOR_BAR_REM : 0) + (tickerItems.size > 0 ? TICKER_REM : 0) + 1}rem`;

  if (!race) {
    const upcoming = nowNext?.nextRace?.label;
    const message = nowNext?.eventName
      ? 'No race on track'
      : isError
        ? 'Waiting for the timing server'
        : 'No event in progress';
    return (
      <div className="p-4">
        <section
          className={cn('inline-block max-w-[26rem] rounded-lg px-4 py-2 font-sans shadow-lg', colours.panel)}
          aria-label="Live timing overlay"
        >
          <p className="text-sm font-semibold">{message}</p>
          {upcoming && <p className={cn('truncate text-xs', colours.muted)}>Next: {upcoming}</p>}
        </section>
        <OverlayTicker
          eventId={tickerEventId}
          raceId={null}
          rows={[]}
          items={tickerItems}
          panelClass={colours.panel}
          bottom={tickerBottom}
          separatorImage={separatorImage}
        />
        {sponsorsShowing && <SponsorBar sponsors={sponsors} panelClass={colours.panel} />}
      </div>
    );
  }

  const elapsed = clock ? clock.elapsedMs + (clock.running ? Math.max(0, now - clockReadAt) : 0) : null;
  const clockText =
    elapsed === null ? null : clock?.durationMs != null ? fmtClock(clock.durationMs - elapsed) : fmtClock(elapsed);

  return (
    <div className="p-4">
      <section
        className={cn(layout === 'tower' ? TOWER_WIDTH_CLASS : 'w-[26rem]', 'overflow-hidden rounded-lg font-sans shadow-lg', colours.panel)}
        aria-label="Live timing overlay"
      >
        <header className="flex items-center gap-3 px-4 py-2">
          {showClass && <h1 className="min-w-0 flex-1 truncate text-base font-semibold">{race.label}</h1>}
          <div className={cn('flex items-center gap-2', !showClass && 'flex-1 justify-between')}>
            {race.status === 'STOPPED' && (
              <span className="rounded bg-red-600 px-2 py-0.5 text-xs font-bold uppercase text-white">Stopped</span>
            )}
            {clockText && (
              <span className="text-xl font-bold tabular-nums" aria-label="Race clock">
                {clockText}
              </span>
            )}
          </div>
        </header>
        {rows.length > 0 && layout === 'tower' && (
          <OverlayTower
            rows={rows}
            allRows={allRows}
            colours={colours}
            lapFlash={lapFlash}
            closeMs={closeMs}
            showArrows={showArrows && race.finalLetter !== null}
            classTags={showClassTags}
            bumpUpPlaces={showBumpLine ? race.bumpUpPlaces ?? 0 : 0}
            barClock={showBars && race.status === 'RUNNING' ? barClock : undefined}
            predicted={showPredicted && clock?.remainingMs != null ? { clock: barClock, remainingAt } : undefined}
            qualifying={qualifying}
            qualifyingFinal={liveQualifying?.phase === 'FINISHED'}
            gap={gapView.view}
            gapFading={gapView.fading}
            lapsView={lapsView.view === 'int' ? 'laps' : 'predicted'}
            lapsFading={lapsView.fading}
          />
        )}
        {showTqPace && isQualifier && layout === 'tower' && race.status === 'RUNNING' && (
          <TqPace
            leader={allRows[0]}
            tq={tqRow}
            clock={barClock}
            remainingAt={remainingAt}
            className={cn('px-2 py-1 text-[11px] font-semibold', colours.muted)}
          />
        )}
        {rows.length > 0 && layout === 'table' && (
          <table className="w-full text-sm" aria-label="Running order">
            <thead className="sr-only">
              <tr>
                <th>Position</th>
                <th>Car</th>
                <th>Driver</th>
                <th>Laps</th>
                <th>Total time</th>
                <th>Last lap</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const flash = lapFlash.get(row.entryId);
                const close = isClose(row, closeMs);
                return (
                  <tr
                    key={row.entryId}
                    data-close={close || undefined}
                    className={cn(
                      'border-t transition-colors',
                      colours.row,
                      close && !flash && 'bg-amber-500/25',
                      flash === 'race-best' && 'bg-purple-500/40',
                      flash === 'personal-best' && 'bg-blue-500/40',
                      flash === 'improving' && 'bg-green-500/40',
                      flash === 'slow' && 'bg-red-500/40',
                    )}
                  >
                    <td className="w-10 py-1 pl-3">
                      <span
                        className={cn(
                          'inline-flex h-6 w-6 items-center justify-center rounded font-bold tabular-nums',
                          colours.position,
                        )}
                      >
                        {row.position}
                      </span>
                                          </td>
                    <td className="w-8 py-1 pr-1 text-right tabular-nums">{row.carNumber ?? ''}</td>
                    <td className="max-w-0 truncate py-1 pr-2 font-medium">{row.driverName}
                      <RowStateIcon flash={flash} close={close} className="ml-1" />
                      {showBars && race.status === 'RUNNING' && <OnPaceIcon row={row} clock={barClock} className="ml-1" />}
                      {showArrows && race.finalLetter !== null && <PositionArrow row={row} rows={allRows} />}
                      {row.finished && <FinishedFlag className="ml-1" />}
                      {showBars && race.status === 'RUNNING' && <LapProgressBar row={row} clock={barClock} className="mt-0.5 h-1" />}
                    </td>
                    <td className="w-12 py-1 pr-2 text-right tabular-nums">{row.lapsCompleted}</td>
                    <td className={cn('w-20 py-1 pr-2 text-right tabular-nums', colours.muted)}>
                      {fmtMs(row.totalTimeMs)}
                    </td>
                    <td className={cn('w-20 py-1 pr-4 text-right tabular-nums', colours.muted)}>
                      {fmtMs(row.lastLapMs)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {showLegend && rows.length > 0 && (
          <TimingLegend kinds={legendKinds} className={cn('border-t px-4 py-1.5 text-[10px]', colours.row, colours.muted)} />
        )}
      </section>
      {/* Battles and fastest laps sit apart from the tower, bottom left, clear of the ticker and sponsor bar; the
          panel hides itself while neither has anything to show. It widens to keep each battle on one line, up to
          the position chart; the stats row ignores its own text width so rolling stats don't resize the panel */}
      <section
        className={cn(
          'fixed left-4 w-max min-w-[26rem] overflow-hidden rounded-lg font-sans shadow-lg empty:hidden [&>*:first-child]:border-t-0',
          colours.panel,
        )}
        style={{ bottom: chartBottom, maxWidth: showChart && chartSeries.length > 0 ? 'calc(100vw - 34rem)' : 'calc(100vw - 2rem)' }}
        aria-label="Battles and fastest laps"
      >
        {showBattles && lapCounted && race.status === 'RUNNING' && (
          <BattleStrip groups={battleGroups} className={cn('border-t px-4 py-1.5 text-sm [&>*]:flex-nowrap [&>*]:whitespace-nowrap', colours.row)} />
        )}
        <StatsTicker rows={allRows} raceId={race.raceId} mutedClass={colours.muted} rowClass={colours.row} />
      </section>
      {showChart && chartSeries.length > 0 && (
        <section
          className={cn('fixed right-4 w-[28rem] rounded-lg px-3 py-2 font-sans shadow-lg', colours.panel)}
          // Clear of the ticker's strip and the sponsor bar when they are showing
          style={{ bottom: chartBottom }}
          aria-label="Position chart"
        >
          <PositionChart series={chartSeries} />
        </section>
      )}
      <OverlayTicker
        eventId={tickerEventId}
        raceId={race.raceId}
        rows={allRows}
        items={tickerItems}
        panelClass={colours.panel}
        bottom={tickerBottom}
        separatorImage={separatorImage}
      />
      {sponsorsShowing && <SponsorBar sponsors={sponsors} panelClass={colours.panel} />}
    </div>
  );
}
