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
}));

import { getBoardLiveTiming, getNowNext, getRaceClock } from '@/lib/boardsApi';

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

  it('flashes a new lap in the colour the live timing screen uses', async () => {
    stompFrames['/topic/race/12/timing'] = [{ ...row(1, 'Grace Hopper', 13_500), overallFastestLapMs: 13_500 }];
    vi.mocked(getNowNext).mockResolvedValue(nowNext(race));
    renderOverlay();

    const driver = await screen.findByText('Grace Hopper');
    expect(driver.closest('tr')).toHaveClass('bg-purple-500/40');
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
    });
    expect(parseOverlayOptions(new URLSearchParams('event=7&top=500')).top).toBe(40);
    expect(parseOverlayOptions(new URLSearchParams('event=7')).eventId).toBe(7);
  });

  it('formats race time as minutes and seconds', () => {
    expect(fmtClock(225_000)).toBe('3:45');
    expect(fmtClock(5_999)).toBe('0:05');
    expect(fmtClock(-1)).toBe('0:00');
  });
});
