import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import NowNextBoard from './NowNextBoard';
import type { BoardRaceDto, NowNextDto } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

// No jsdom WebSocket: mock the STOMP hook and answer per topic.
const stompFrames: Record<string, unknown> = {};
const mockUseStomp = vi.fn((topic: string | null) => ({
  data: topic ? (stompFrames[topic] ?? null) : null,
  status: topic ? 'connected' : 'disconnected',
}));
vi.mock('@/hooks/race-control/useStomp', () => ({
  useStomp: (topic: string | null) => mockUseStomp(topic),
}));

vi.mock('@/lib/boardsApi', () => ({
  getNowNext: vi.fn(),
  getResultsBoard: vi.fn(),
  getBoardLiveTiming: vi.fn(),
  getTicker: vi.fn(),
}));

import { getBoardLiveTiming, getNowNext, getResultsBoard, getTicker } from '@/lib/boardsApi';

function race(overrides: Partial<BoardRaceDto>): BoardRaceDto {
  return {
    raceId: 1,
    label: 'Qualifying 1 — Touring Stock — Heat 1',
    roundType: 'QUALIFIER',
    roundNumber: 1,
    className: 'Touring Stock',
    heatNumber: 1,
    finalLetter: null,
    status: 'RUNNING',
    ...overrides,
  };
}

function nowNext(overrides: Partial<NowNextDto>): NowNextDto {
  return {
    eventId: 7,
    eventName: 'Club Round 3',
    currentRace: null,
    nextRace: null,
    lastCompletedRace: null,
    ...overrides,
  };
}

function liveRow(overrides: Partial<LiveTimingRowDto>): LiveTimingRowDto {
  return {
    entryId: 1,
    driverName: 'Jane Doe',
    carNumber: '7',
    position: 1,
    lapsCompleted: 5,
    lastPassingTimeMs: 0,
    lastLapMs: 19_250,
    bestLapMs: 18_900,
    avgLapMs: null,
    overallFastestLapMs: null,
    lapsDown: 0,
    intervalLapsDown: 0,
    gapToLeaderMs: null,
    gapToAheadMs: null,
    totalTimeMs: null,
    ...overrides,
  };
}

function renderBoard(path = '/boards/now-next') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <NowNextBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  for (const key of Object.keys(stompFrames)) delete stompFrames[key];
  vi.mocked(getBoardLiveTiming).mockResolvedValue([]);
  vi.mocked(getTicker).mockResolvedValue({ nextRace: null, nextDrivers: [], marshals: [], topQualifiers: [] });
  vi.mocked(getResultsBoard).mockResolvedValue({
    eventId: 7,
    eventName: 'Club Round 3',
    race: null,
    results: [],
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('NowNextBoard', () => {
  it('follows the running race on its STOMP timing topic and shows live rows', async () => {
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        currentRace: race({ raceId: 11 }),
        nextRace: race({
          raceId: 12,
          label: 'Qualifying 1 — Touring Stock — Heat 2',
          status: 'PENDING',
        }),
      }),
    );
    stompFrames['/topic/race/11/timing'] = [
      liveRow({ entryId: 2, driverName: 'Sam Second', position: 2, lapsCompleted: 4, lapsDown: 1 }),
      liveRow({ entryId: 1, driverName: 'Jane Doe', position: 1 }),
    ];

    renderBoard();

    expect(await screen.findByText('Qualifying 1 — Touring Stock — Heat 1')).toBeInTheDocument();
    expect(screen.getByText('Racing')).toBeInTheDocument();
    const names = within(screen.getByRole('list', { name: 'Line-up' }))
      .getAllByRole('listitem')
      .map((r) => r.textContent);
    expect(names[0]).toContain('Jane Doe');
    expect(names[1]).toContain('Sam Second');
    // timing and the chart live on the Live Race screen
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText('+1 lap')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Position by lap' })).not.toBeInTheDocument();
    expect(screen.getByText('Qualifying 1 — Touring Stock — Heat 2')).toBeInTheDocument();
    expect(mockUseStomp).toHaveBeenCalledWith('/topic/race/11/timing');
    expect(mockUseStomp).toHaveBeenCalledWith('/topic/race/11/state');
  });

  it('lists the next drivers and marshals under the next race', async () => {
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        currentRace: race({ raceId: 11 }),
        nextRace: race({ raceId: 12, label: 'Qualifying 1 — Touring Stock — Heat 2', status: 'PENDING' }),
      }),
    );
    vi.mocked(getTicker).mockResolvedValue({
      nextRace: 'Qualifying 1 — Touring Stock — Heat 2',
      nextDrivers: ['Ann Next', 'Bob Next'],
      marshals: ['Cat Marshal'],
      topQualifiers: [],
    });

    renderBoard();

    expect(await screen.findByText('Ann Next')).toBeInTheDocument();
    expect(screen.getByText('Bob Next')).toBeInTheDocument();
    expect(screen.getByTestId('board-next-marshals')).toHaveTextContent('Cat Marshal');
  });

  it('seeds the live table from the public snapshot before the first STOMP frame', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race({ raceId: 11 }) }));
    vi.mocked(getBoardLiveTiming).mockResolvedValue([liveRow({ driverName: 'Seeded Racer' })]);

    renderBoard();

    expect(await screen.findByText('Seeded Racer')).toBeInTheDocument();
    expect(getBoardLiveTiming).toHaveBeenCalledWith(11);
  });

  it('shows a stopped race as stopped', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race({ status: 'STOPPED' }) }));

    renderBoard();

    expect(await screen.findByText('Stopped')).toBeInTheDocument();
    expect(screen.getByText('Waiting for the line-up…')).toBeInTheDocument();
  });

  it('between races shows what is next and the last finished results', async () => {
    const finished = race({ raceId: 10, label: 'A Final — 1/10 Buggy', status: 'FINISHED' });
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        nextRace: race({
          raceId: 12,
          label: 'Qualifying 2 — Touring Stock — Heat 1',
          status: 'GRID',
        }),
        lastCompletedRace: finished,
      }),
    );
    vi.mocked(getResultsBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      race: finished,
      results: [
        {
          position: 1,
          entryId: 1,
          driverName: 'Winner Person',
          carNumber: '7',
          lapsCompleted: 20,
          totalTimeMs: 300_000,
          bestLapMs: 14_500,
          gapToLeaderMs: null,
        },
      ],
    });

    renderBoard();

    expect(await screen.findByText('Winner Person')).toBeInTheDocument();
    expect(screen.getByText('Qualifying 2 — Touring Stock — Heat 1')).toBeInTheDocument();
    expect(screen.getByText('Last finished: A Final — 1/10 Buggy')).toBeInTheDocument();
    expect(mockUseStomp).not.toHaveBeenCalledWith(expect.stringContaining('/topic/'));
  });

  it('says so when there is nothing to show', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({}));

    renderBoard();

    expect(await screen.findByText('No race data yet.')).toBeInTheDocument();
  });

  it('passes ?event= through to the board endpoint', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({}));

    renderBoard('/boards/now-next?event=42');

    await screen.findByText('No race data yet.');
    expect(getNowNext).toHaveBeenCalledWith(42);
  });

  it('says it cannot reach the server before its first answer', async () => {
    vi.mocked(getNowNext).mockRejectedValue(new Error('Network Error'));

    renderBoard();

    expect(await screen.findByText('Can’t reach the timing system. Trying again…')).toBeInTheDocument();
  });

  it('counts down to the next start when auto run is waiting', async () => {
    const serverTime = '2026-10-17T12:00:00Z';
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        nextRace: race({ raceId: 12, status: 'PENDING' }),
        autoRun: { phase: 'WAITING', heldReason: null, startAt: '2026-10-17T12:02:05Z', serverTime },
      }),
    );
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-10-17T12:00:00Z').getTime());

    renderBoard();

    expect(await screen.findByTestId('board-next-start')).toHaveTextContent('2:05');
  });

  it('counts against the server clock when this browser clock is minutes out', async () => {
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        nextRace: race({ raceId: 12, status: 'PENDING' }),
        autoRun: {
          phase: 'WAITING',
          heldReason: null,
          startAt: '2026-10-17T12:02:05Z',
          serverTime: '2026-10-17T12:00:00Z',
        },
      }),
    );
    // This browser thinks it is five minutes later than the server does.
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-10-17T12:05:00Z').getTime());

    renderBoard();

    expect(await screen.findByTestId('board-next-start')).toHaveTextContent('2:05');
  });

  it('shows a waiting message, not a count, while auto run holds before the finals', async () => {
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({
        nextRace: race({ raceId: 12, status: 'PENDING' }),
        autoRun: { phase: 'HELD', heldReason: 'FINALS', startAt: null, serverTime: '2026-10-17T12:00:00Z' },
      }),
    );

    renderBoard();

    expect(await screen.findByText('Waiting for the finals to be released')).toBeInTheDocument();
    expect(screen.queryByTestId('board-next-start')).not.toBeInTheDocument();
  });
});
