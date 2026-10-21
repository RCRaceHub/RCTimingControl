// Streaming overlay for an OBS browser source (#29): the race in progress on a transparent page, with its
// running order, laps, last lap and race clock. Anonymous, like the other boards. It follows the race's STOMP
// timing and state topics (useBoardRace), and shows a small idle panel while no race is on track, so a blank page
// never looks like a broken overlay.
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { cn } from '@/lib/utils';
import { getRaceClock, getRoundFastestLap, getTicker } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import {
  BOARD_POLL_MS,
  fmtClock,
  heatBestLap,
  parseOverlayOptions,
  roundBestLap,
  tickerSegments,
  type TickerItem,
} from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { useLivePositionSeries } from './useLivePositionSeries';
import { PositionChart } from '@/components/print/PositionChart';
import { useLapFlash } from '@/hooks/race-control/useLapFlash';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { FinishedFlag } from '@/components/FinishedFlag';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const THEMES = {
  dark: {
    panel: 'bg-slate-950/80 text-white',
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

/** How long each ticker stat stays up before the next one rolls in. */
const TICKER_MS = 5000;

/** Rolls through the heat's total laps, its fastest lap and the round's fastest lap, one stat at a time. */
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
  const { data: round } = useQuery({
    queryKey: publicQueryKeys.boards.roundFastest(raceId),
    queryFn: () => getRoundFastestLap(raceId),
    refetchInterval: BOARD_POLL_MS * 6,
  });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), TICKER_MS);
    return () => clearInterval(timer);
  }, []);

  const totalLaps = rows.reduce((sum, row) => sum + row.lapsCompleted, 0);
  const heatBest = heatBestLap(rows);
  // The round's fastest includes this heat, which has no snapshot until it finishes
  const roundBest = roundBestLap(heatBest, round);

  const stats: Array<[string, string]> = [];
  if (totalLaps > 0) stats.push(['Laps in heat', String(totalLaps)]);
  if (heatBest) stats.push(['Fastest lap', `${fmtMs(heatBest.lapMs)} · ${heatBest.driverName}`]);
  if (roundBest) stats.push(['Round fastest', `${fmtMs(roundBest.lapMs)} · ${roundBest.driverName}`]);
  if (stats.length === 0) return null;

  const [label, value] = stats[tick % stats.length];
  return (
    <div
      className={cn('flex items-baseline gap-2 border-t px-4 py-1.5 text-sm', rowClass)}
      aria-label="Race stats"
      aria-live="off"
    >
      <span className={cn('text-xs uppercase tracking-wide', mutedClass)}>{label}</span>
      <span className="min-w-0 flex-1 truncate font-semibold tabular-nums">{value}</span>
    </div>
  );
}

/** Seconds a ticker takes per character to cross the screen: slow enough to read, long text just takes longer. */
const TICKER_SECONDS_PER_CHAR = 0.18;
const TICKER_MIN_SECONDS = 20;

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
}: {
  eventId: number | null;
  raceId: number | null;
  rows: LiveTimingRowDto[];
  items: Set<TickerItem>;
  panelClass: string;
}) {
  const { data: ticker } = useQuery({
    queryKey: publicQueryKeys.boards.ticker(eventId),
    queryFn: () => getTicker(eventId),
    enabled: items.size > 0,
    refetchInterval: BOARD_POLL_MS * 4,
  });
  const { data: round } = useQuery({
    queryKey: publicQueryKeys.boards.roundFastest(raceId),
    queryFn: () => getRoundFastestLap(raceId!),
    enabled: raceId !== null && items.has('fastest'),
    refetchInterval: BOARD_POLL_MS * 6,
  });
  const segments = tickerSegments(items, ticker, roundBestLap(heatBestLap(rows), round));
  if (segments.length === 0) return null;
  const text = segments.join('   •   ');
  return (
    <div
      className={cn('fixed inset-x-0 bottom-0 overflow-hidden py-2 font-sans text-lg font-semibold', panelClass)}
      aria-label="Ticker"
    >
      <span
        key={text}
        className="ticker-scroll"
        style={{ animationDuration: `${Math.max(TICKER_MIN_SECONDS, text.length * TICKER_SECONDS_PER_CHAR)}s` }}
      >
        {text}
      </span>
    </div>
  );
}

export default function OverlayBoard() {
  const [searchParams] = useSearchParams();
  const { eventId, top, showClass, theme, showChart, tickerItems } = parseOverlayOptions(searchParams);
  const colours = THEMES[theme];
  useTransparentPage();

  const { nowNext, isError, currentRace: race, raceId, rows: allRows } = useBoardRace(eventId);
  const { data: clock, dataUpdatedAt: clockReadAt } = useQuery({
    queryKey: publicQueryKeys.boards.clock(raceId),
    queryFn: () => getRaceClock(raceId!),
    enabled: raceId !== null,
    refetchInterval: BOARD_POLL_MS,
  });

  const now = useNow(!!clock?.running);
  const rows = allRows.slice(0, top);
  // Judged on the whole field, so a driver below the cut-off still keeps their lap history
  const lapFlash = useLapFlash(allRows);
  const chartSeries = useLivePositionSeries(raceId, allRows);

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
          eventId={eventId ?? nowNext?.eventId ?? null}
          raceId={null}
          rows={[]}
          items={tickerItems}
          panelClass={colours.panel}
        />
      </div>
    );
  }

  const elapsed = clock ? clock.elapsedMs + (clock.running ? Math.max(0, now - clockReadAt) : 0) : null;
  const clockText =
    elapsed === null ? null : clock?.durationMs != null ? fmtClock(clock.durationMs - elapsed) : fmtClock(elapsed);

  return (
    <div className="p-4">
      <section
        className={cn('w-[26rem] overflow-hidden rounded-lg font-sans shadow-lg', colours.panel)}
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
        {rows.length > 0 && (
          <table className="w-full text-sm" aria-label="Running order">
            <thead className="sr-only">
              <tr>
                <th>Position</th>
                <th>Car</th>
                <th>Driver</th>
                <th>Laps</th>
                <th>Last lap</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const flash = lapFlash.get(row.entryId);
                return (
                  <tr
                    key={row.entryId}
                    className={cn(
                      'border-t transition-colors',
                      colours.row,
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
                    <td className="max-w-0 truncate py-1 pr-2 font-medium">{row.driverName}{row.finished && <FinishedFlag className="ml-1" />}</td>
                    <td className="w-12 py-1 pr-2 text-right tabular-nums">{row.lapsCompleted}</td>
                    <td className={cn('w-20 py-1 pr-4 text-right tabular-nums', colours.muted)}>
                      {fmtMs(row.lastLapMs)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        <StatsTicker rows={allRows} raceId={race.raceId} mutedClass={colours.muted} rowClass={colours.row} />
      </section>
      {showChart && chartSeries.length > 0 && (
        <section
          className={cn('fixed bottom-14 right-4 w-[28rem] rounded-lg px-3 py-2 font-sans shadow-lg', colours.panel)}
          aria-label="Position chart"
        >
          <PositionChart series={chartSeries} />
        </section>
      )}
      <OverlayTicker
        eventId={eventId ?? nowNext?.eventId ?? null}
        raceId={race.raceId}
        rows={allRows}
        items={tickerItems}
        panelClass={colours.panel}
      />
    </div>
  );
}
