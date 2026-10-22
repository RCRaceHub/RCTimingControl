import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import ResultsBoard from './ResultsBoard';

vi.mock('@/lib/boardsApi', () => ({
  getResultsBoard: vi.fn(),
}));

import { getResultsBoard } from '@/lib/boardsApi';

function renderBoard(path = '/boards/results') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <ResultsBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('ResultsBoard', () => {
  it('shows the last finished race with lapped finishers marked', async () => {
    vi.mocked(getResultsBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      race: {
        raceId: 10,
        label: 'A Final — 1/10 Buggy',
        roundType: 'FINAL',
        roundNumber: 1,
        className: '1/10 Buggy',
        heatNumber: 1,
        finalLetter: 'A',
        status: 'FINISHED',
      },
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
        {
          position: 2,
          entryId: 2,
          driverName: 'Close Second',
          carNumber: null,
          lapsCompleted: 20,
          totalTimeMs: 301_250,
          bestLapMs: 14_600,
          gapToLeaderMs: 1_250,
        },
        {
          position: 3,
          entryId: 3,
          driverName: 'Lapped Third',
          carNumber: '3',
          lapsCompleted: 18,
          totalTimeMs: 305_000,
          bestLapMs: 15_100,
          gapToLeaderMs: 5_000,
        },
      ],
    });

    renderBoard();

    expect(await screen.findByText('A Final — 1/10 Buggy')).toBeInTheDocument();
    expect(screen.getByText('Club Round 3')).toBeInTheDocument();
    expect(screen.getByText('14.500')).toBeInTheDocument();
    expect(screen.getByText('+1.250')).toBeInTheDocument();
    expect(screen.getByText('+2 laps')).toBeInTheDocument();
  });

  it('says so when nothing has finished', async () => {
    vi.mocked(getResultsBoard).mockResolvedValue({
      eventId: null,
      eventName: null,
      race: null,
      results: [],
    });

    renderBoard('/boards/results?event=5');

    expect(await screen.findByText('No results yet.')).toBeInTheDocument();
    expect(getResultsBoard).toHaveBeenCalledWith(5);
  });

  it('says it cannot reach the server rather than that nothing has finished', async () => {
    vi.mocked(getResultsBoard).mockRejectedValue(new Error('Network Error'));

    renderBoard();

    expect(await screen.findByText('Can’t reach the timing system. Trying again…')).toBeInTheDocument();
    expect(screen.queryByText('No results yet.')).not.toBeInTheDocument();
  });

  it('shows the A final overall under the leg result when the final runs in legs', async () => {
    vi.mocked(getResultsBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      race: {
        raceId: 12,
        label: 'A Final — 1/10 Buggy — Leg 2',
        roundType: 'FINAL',
        roundNumber: 1,
        className: '1/10 Buggy',
        heatNumber: 2,
        finalLetter: 'A',
        status: 'FINISHED',
      },
      results: [],
      legs: {
        eventClassId: 3,
        className: '1/10 Buggy',
        legs: 3,
        legsFinished: 2,
        legsCounted: 2,
        complete: false,
        rows: [
          { position: 1, entryId: 1, competitorId: 1, driverName: 'Leader Person', carNumber: '7', points: 2, laps: 25, timeMs: 600_000, legPoints: [1, 1], counted: [true, true] },
          { position: 2, entryId: 2, competitorId: 2, driverName: 'Chaser Person', carNumber: '8', points: 4, laps: 24, timeMs: 600_000, legPoints: [2, 2], counted: [true, true] },
        ],
      },
    });

    renderBoard();

    expect(await screen.findByText('A Final after 2 of 3 legs')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Overall' })).toBeInTheDocument();
    expect(screen.getByText('Leader Person')).toBeInTheDocument();
  });
});
