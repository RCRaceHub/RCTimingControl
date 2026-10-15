// Streaming overlay for an OBS browser source (#29): the race in progress on a transparent page, with its
// running order, laps, last lap and race clock. Anonymous, like the other boards. It follows the race's STOMP
// timing and state topics (useBoardRace), and shows nothing while no race is on track so the stream stays clear.
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { cn } from '@/lib/utils';
import { getRaceClock } from '@/lib/boardsApi';
import { fmtMs } from '@/lib/format';
import { BOARD_POLL_MS, fmtClock, parseOverlayOptions } from './boardFormat';
import { useBoardRace } from './useBoardRace';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { FinishedFlag } from '@/components/FinishedFlag';

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

export default function OverlayBoard() {
  const [searchParams] = useSearchParams();
  const { eventId, top, showClass, theme } = parseOverlayOptions(searchParams);
  const colours = THEMES[theme];
  useTransparentPage();

  const { currentRace: race, raceId, rows: allRows } = useBoardRace(eventId);
  const { data: clock, dataUpdatedAt: clockReadAt } = useQuery({
    queryKey: publicQueryKeys.boards.clock(raceId),
    queryFn: () => getRaceClock(raceId!),
    enabled: raceId !== null,
    refetchInterval: BOARD_POLL_MS,
  });

  const now = useNow(!!clock?.running);
  const rows = allRows.slice(0, top);

  if (!race) return null;

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
                <th>Driver</th>
                <th>Laps</th>
                <th>Last lap</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.entryId} className={cn('border-t', colours.row)}>
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
                  <td className="max-w-0 truncate py-1 pr-2 font-medium">{row.driverName}{row.finished && <FinishedFlag className="ml-1" />}</td>
                  <td className="w-12 py-1 pr-2 text-right tabular-nums">{row.lapsCompleted}</td>
                  <td className={cn('w-20 py-1 pr-4 text-right tabular-nums', colours.muted)}>
                    {fmtMs(row.lastLapMs)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
