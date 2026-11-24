import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import LiveRaceBoard from './LiveRaceBoard';
import type { NowNextDto } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

vi.mock('@/hooks/race-control/useStomp', () => ({
  useStomp: () => ({ data: null, status: 'connected' }),
}));

vi.mock('@/lib/boardsApi', () => ({
  getNowNext: vi.fn(),
  getBoardLiveTiming: vi.fn(),
  getPositionHistory: vi.fn(),
  getRaceClock: vi.fn(),
  getForcedClose: vi.fn(),
  getRaceMedia: vi.fn().mockResolvedValue([]),
}));

import { getBoardLiveTiming, getForcedClose, getNowNext, getPositionHistory, getRaceClock } from '@/lib/boardsApi';

const race = {
  raceId: 12,
  label: 'Qualifying 1 — Stock Buggy — Heat 2',
  roundType: 'QUALIFIER',
  roundNumber: 1,
  className: 'Stock Buggy',
  heatNumber: 2,
  finalLetter: null,
  status: 'RUNNING',
} as const;

function nowNext(overrides: Partial<NowNextDto>): NowNextDto {
  return { eventId: 7, eventName: 'Club Round 3', currentRace: null, nextRace: null, lastCompletedRace: null, ...overrides };
}

function row(position: number): LiveTimingRowDto {
  return { entryId: position, driverName: `Driver ${position}`, carNumber: String(position), position, lapsCompleted: 2 } as LiveTimingRowDto;
}

function renderBoard(path = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <LiveRaceBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LiveRaceBoard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('draws the chart of the race on track', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race }));
    vi.mocked(getBoardLiveTiming).mockResolvedValue([row(1), row(2)]);
    vi.mocked(getPositionHistory).mockResolvedValue([
      { lap: 1, entryId: 1, position: 2 },
      { lap: 1, entryId: 2, position: 1 },
      { lap: 2, entryId: 1, position: 1 },
      { lap: 2, entryId: 2, position: 2 },
    ]);
    renderBoard();

    expect(await screen.findByText(race.label)).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'Live timing' })).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'Position by lap' })).toBeInTheDocument();
  });

  it('says what is next while nothing is on track', async () => {
    vi.mocked(getNowNext).mockResolvedValue(
      nowNext({ nextRace: { ...race, raceId: 13, label: 'Qualifying 1 — Stock Buggy — Heat 3', status: 'PENDING' } }),
    );
    renderBoard();

    expect(await screen.findByText('Next up: Qualifying 1 — Stock Buggy — Heat 3')).toBeInTheDocument();
  });

  it('shows the time left on the race clock', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race }));
    vi.mocked(getBoardLiveTiming).mockResolvedValue([row(1)]);
    vi.mocked(getPositionHistory).mockResolvedValue([]);
    vi.mocked(getRaceClock).mockResolvedValue({ raceId: 12, status: 'RUNNING', elapsedMs: 75_000, durationMs: 300_000, remainingMs: 225_000, running: false });
    renderBoard();

    expect(await screen.findByLabelText('Race clock')).toHaveTextContent('3:45');
  });

  it('counts down the time left to finish in red once the race time is up, or in the colour asked for', async () => {
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race }));
    vi.mocked(getBoardLiveTiming).mockResolvedValue([row(1)]);
    vi.mocked(getPositionHistory).mockResolvedValue([]);
    vi.mocked(getRaceClock).mockResolvedValue({ raceId: 12, status: 'RUNNING', elapsedMs: 301_000, durationMs: 300_000, remainingMs: 0, running: true });
    vi.mocked(getForcedClose).mockResolvedValue({ closesInMs: 42_000 });
    const { unmount } = renderBoard();

    const left = await screen.findByLabelText('Time left to finish');
    await waitFor(() => expect(left).toHaveTextContent('0:42'));
    expect(left).toHaveClass('text-destructive');
    unmount();

    renderBoard('/?overrun=ff8800');
    expect(await screen.findByLabelText('Time left to finish')).toHaveStyle({ color: '#ff8800' });
  });
});
