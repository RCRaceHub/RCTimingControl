import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import OverlayBoard from './OverlayBoard';
import { fmtClock, parseOverlayOptions } from './boardFormat';
import type { BoardRaceDto, NowNextDto, RaceClockDto } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

// No jsdom WebSocket: mock the STOMP hook and answer per topic.
const stompFrames: Record<string, unknown> = {};
vi.mock('@/hooks/race-control/useStomp', () => ({
  useStomp: (topic: string | null) => ({
    data: topic ? (stompFrames[topic] ?? null) : null,
    status: topic ? 'connected' : 'disconnected',
  }),
}));

vi.mock('@/lib/boardsApi', () => ({
  getNowNext: vi.fn(),
  getBoardLiveTiming: vi.fn(),
  getRaceClock: vi.fn(),
  getRoundFastestLap: vi.fn(),
  getPositionHistory: vi.fn(),
  getTicker: vi.fn(),
}));

import {
  getBoardLiveTiming,
  getNowNext,
  getPositionHistory,
  getRaceClock,
  getRoundFastestLap,
  getTicker,
} from '@/lib/boardsApi';

const race: BoardRaceDto = {
  raceId: 12,
  label: 'Qualifying 1 — Stock Buggy — Heat 2',
  roundType: 'QUALIFIER',
  roundNumber: 1,
  className: 'Stock Buggy',
  heatNumber: 2,
  finalLetter: null,
  status: 'RUNNING',
};

function nowNext(currentRace: BoardRaceDto | null): NowNextDto {
  return { eventId: 7, eventName: 'Club Round 3', currentRace, nextRace: null, lastCompletedRace: null };
}

function clock(overrides: Partial<RaceClockDto> = {}): RaceClockDto {
  return {
    raceId: 12,
    status: 'RUNNING',
    elapsedMs: 75_000,
    durationMs: 300_000,
    remainingMs: 225_000,
    running: false,
    ...overrides,
  };
}

function row(position: number, driverName: string, lastLapMs: number): LiveTimingRowDto {
  return {
    entryId: position,
    driverName,
    carNumber: String(position),
    position,
    lapsCompleted: 10 - position,
    lastPassingTimeMs: 0,
    lastLapMs,
    bestLapMs: lastLapMs,
    avgLapMs: null,
    overallFastestLapMs: null,
    lapsDown: 0,
    intervalLapsDown: 0,
    gapToLeaderMs: null,
    gapToAheadMs: null,
    totalTimeMs: null,
  };
}

function renderOverlay(path = '/boards/overlay') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <OverlayBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  for (const key of Object.keys(stompFrames)) delete stompFrames[key];
  vi.mocked(getBoardLiveTiming).mockResolvedValue([
    row(1, 'Ada Lovelace', 13_870),
    row(2, 'Grace Hopper', 14_020),
    row(3, 'Walk-in Wendy', 15_400),
  ]);
  vi.mocked(getRaceClock).mockResolvedValue(clock());
  vi.mocked(getRoundFastestLap).mockResolvedValue({ lapMs: null, driverName: null });
  vi.mocked(getPositionHistory).mockResolvedValue([]);
  vi.mocked(getTicker).mockResolvedValue({ nextRace: null, nextDrivers: [], marshals: [], currentMarshals: [], marshalsPending: false, topQualifiers: [] });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('OverlayBoard', () => {
  it('shows a small idle panel on a transparent page while no race is on track', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(null));
    renderOverlay();

    expect(await screen.findByText('No race on track')).toBeInTheDocument();
    expect(document.body.style.background).toBe('transparent');
    expect(document.documentElement.style.background).toBe('transparent');
  });

  it('shows the race, its running order, laps, last lap and time to go', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    renderOverlay();

    expect(await screen.findByText('Qualifying 1 — Stock Buggy — Heat 2')).toBeInTheDocument();
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('13.870')).toBeInTheDocument();
    expect(await screen.findByLabelText('Race clock')).toHaveTextContent('3:45');
  });

  it('adds lap bars, a close tint and, in a final, places-gained arrows, each switchable from the address', async () => {
    const close = { ...row(2, 'Grace Hopper', 13_500), gapToAheadMs: 600, startPosition: 4, avgLapMs: 13_000, lastPassingTimeMs: Date.now() };
    stompFrames['/topic/race/12/timing'] = [{ ...row(1, 'Ada Lovelace', 13_870), startPosition: 5 }, close];
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ ...race, finalLetter: 'A' }));
    const { container, unmount } = renderOverlay();

    expect(await screen.findByLabelText('Up 1')).toBeInTheDocument();
    expect(screen.getByTestId('lap-progress')).toBeInTheDocument();
    expect(container.querySelector('tr[data-close]')).not.toBeNull();
    unmount();

    renderOverlay('/boards/overlay?arrows=hide&bars=hide&close=0');
    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByLabelText('Up 1')).toBeNull();
    expect(screen.queryByTestId('lap-progress')).toBeNull();
  });

  it('flashes a new lap in the colour the live timing screen uses', async () => {
    stompFrames['/topic/race/12/timing'] = [{ ...row(1, 'Grace Hopper', 13_500), overallFastestLapMs: 13_500 }];
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    renderOverlay();

    const driver = await screen.findByText('Grace Hopper');
    expect(driver.closest('tr')).toHaveClass('bg-purple-500/40');
  });

  it('ticker starts with the heat\'s total laps, then the fastest lap and the round\'s fastest', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stompFrames['/topic/race/12/timing'] = [row(1, 'Grace Hopper', 13_500), row(2, 'Ada Lovelace', 14_100)];
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    vi.mocked(getRoundFastestLap).mockResolvedValue({ lapMs: 12_900, driverName: 'Katherine Johnson' });
    renderOverlay();

    const ticker = await screen.findByLabelText('Race stats');
    expect(ticker).toHaveTextContent('Laps in heat17');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(ticker).toHaveTextContent('Grace Hopper');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(ticker).toHaveTextContent('Katherine Johnson');
  });

  it('follows the live timing the app pushes', async () => {
    stompFrames['/topic/race/12/timing'] = [row(1, 'Grace Hopper', 13_500), row(2, 'Ada Lovelace', 14_100)];
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    renderOverlay();

    const rows = await screen.findAllByRole('row');
    expect(rows[1]).toHaveTextContent('Grace Hopper');
    expect(screen.queryByText('Walk-in Wendy')).not.toBeInTheDocument();
  });

  it('takes the top N, hides the class and uses the light theme from the query string', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    renderOverlay('/boards/overlay?top=2&class=hide&theme=light');

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByText('Walk-in Wendy')).not.toBeInTheDocument();
    expect(screen.queryByText('Qualifying 1 — Stock Buggy — Heat 2')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Live timing overlay')).toHaveClass('bg-white/85');
  });

  it('marks a stopped race', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ ...race, status: 'STOPPED' }));
    vi.mocked(getRaceClock).mockResolvedValue(clock({ status: 'STOPPED' }));
    renderOverlay();

    expect(await screen.findByText('Stopped')).toBeInTheDocument();
  });

  it('counts the clock on between updates while the race runs', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    vi.mocked(getRaceClock).mockResolvedValue(clock({ running: true }));
    renderOverlay();

    const raceClock = await screen.findByLabelText('Race clock');
    expect(raceClock).toHaveTextContent('3:45');

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(raceClock).toHaveTextContent('3:42');
  });

  it('counts up for a race with no set length', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    vi.mocked(getRaceClock).mockResolvedValue(clock({ durationMs: null, remainingMs: null }));
    renderOverlay();

    expect(await screen.findByLabelText('Race clock')).toHaveTextContent('1:15');
  });
});

describe('overlay options', () => {
  it('falls back to the defaults for anything it does not recognise', () => {
    expect(parseOverlayOptions(new URLSearchParams('top=abc&theme=neon&class=yes'))).toEqual({
      eventId: null,
      top: 10,
      showClass: true,
      theme: 'dark',
      showChart: true,
      tickerItems: new Set(['next', 'drivers', 'marshals', 'tq', 'fastest']),
      showBars: true,
      showArrows: true,
      showPredicted: true,
      closeMs: 1000,
    });
    expect(parseOverlayOptions(new URLSearchParams('event=7&top=500')).top).toBe(40);
    expect(parseOverlayOptions(new URLSearchParams('event=7')).eventId).toBe(7);
  });

  it('reads which ticker items and whether the chart to show from the address', () => {
    expect(parseOverlayOptions(new URLSearchParams('ticker=hide')).tickerItems.size).toBe(0);
    expect([...parseOverlayOptions(new URLSearchParams('ticker=next,tq,bogus')).tickerItems]).toEqual(['next', 'tq']);
    expect(parseOverlayOptions(new URLSearchParams('chart=hide')).showChart).toBe(false);
  });

  it('formats race time as minutes and seconds', () => {
    expect(fmtClock(225_000)).toBe('3:45');
    expect(fmtClock(5_999)).toBe('0:05');
    expect(fmtClock(-1)).toBe('0:00');
  });
});

describe('OverlayBoard chart and ticker', () => {
  it('draws the position chart for the race on track unless the address hides it', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    vi.mocked(getPositionHistory).mockResolvedValue([
      { lap: 1, entryId: 1, position: 2 },
      { lap: 1, entryId: 2, position: 1 },
      { lap: 2, entryId: 1, position: 1 },
      { lap: 2, entryId: 2, position: 2 },
    ]);
    const first = renderOverlay();
    expect(await screen.findByRole('img', { name: 'Position by lap' })).toBeInTheDocument();
    first.unmount();

    renderOverlay('/boards/overlay?chart=hide');
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Position by lap' })).not.toBeInTheDocument();
  });

  it('scrolls the next race, its drivers and marshals, the top qualifier and the round\'s fastest lap', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    vi.mocked(getTicker).mockResolvedValue({
      nextRace: 'Qualifying 1 — Stock Buggy — Heat 3',
      nextDrivers: ['Ada Lovelace', 'Grace Hopper'],
      marshals: ['Walk-in Wendy'],
      currentMarshals: [],
      marshalsPending: false,
      topQualifiers: [{ className: 'Stock Buggy', driverName: 'Ada Lovelace', bestLapMs: 13_500 }],
    });
    vi.mocked(getRoundFastestLap).mockResolvedValue({ lapMs: 12_900, driverName: 'Katherine Johnson' });
    renderOverlay();

    // The ticker is read afresh each time: it is a different element once the race has loaded
    const ticker = () => screen.getByLabelText('Ticker');
    await vi.waitFor(() => expect(ticker()).toHaveTextContent('Next: Qualifying 1 — Stock Buggy — Heat 3'));
    expect(ticker()).toHaveTextContent('On the grid: Ada Lovelace, Grace Hopper');
    expect(ticker()).toHaveTextContent('Marshals: Walk-in Wendy');
    expect(ticker()).toHaveTextContent('TQ Stock Buggy: Ada Lovelace (13.500)');
    await vi.waitFor(() =>
      expect(ticker()).toHaveTextContent('Fastest lap of the round: 12.900 · Katherine Johnson'),
    );
  });

  it('keeps the ticker up between races and leaves out items the address does not name', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext(null));
    vi.mocked(getTicker).mockResolvedValue({
      nextRace: 'Qualifying 2 — Stock Buggy — Heat 1',
      nextDrivers: ['Ada Lovelace'],
      marshals: [],
      currentMarshals: [],
      marshalsPending: false,
      topQualifiers: [],
    });
    renderOverlay('/boards/overlay?ticker=next');

    const ticker = await screen.findByLabelText('Ticker');
    expect(ticker).toHaveTextContent('Next: Qualifying 2 — Stock Buggy — Heat 1');
    expect(ticker).not.toHaveTextContent('On the grid');
  });
});
