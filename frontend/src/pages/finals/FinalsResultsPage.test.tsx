import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import FinalsResultsPage from './FinalsResultsPage';

vi.mock('@/lib/boardsApi', () => ({ getFinalsBoard: vi.fn() }));
vi.mock('@/lib/api', () => ({
  default: {},
  getAboutInfo: vi.fn().mockResolvedValue({ version: '1', buildTime: '', addresses: [] }),
}));

import { getFinalsBoard } from '@/lib/boardsApi';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/finals?event=7']}>
        <FinalsResultsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const row = (position: number, entryId: number, driverName: string) => ({
  position, entryId, driverName, carNumber: String(position), lapsCompleted: 20, totalTimeMs: 300000,
  bestLapMs: 14000, gapToLeaderMs: null,
});

beforeEach(() => vi.clearAllMocks());

describe('FinalsResultsPage', () => {
  it('shows each final of a class, the A final from its legs, and a final not run yet', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      classes: [{
        eventClassId: 1,
        className: '1/10 Buggy',
        finalsFinished: 2,
        finalsTotal: 3,
        finals: [
          {
            letter: 'A', status: 'FINISHED', results: [row(1, 1, 'Leg Leader')],
            legs: {
              eventClassId: 1, className: '1/10 Buggy', legs: 3, legsFinished: 3, legsCounted: 2, complete: true,
              rows: [{
                position: 1, entryId: 2, competitorId: null, driverName: 'Overall Winner', carNumber: '2',
                points: 2, laps: 60, timeMs: 900000, legPoints: [1, 1, 3], counted: [true, true, false],
              }],
            },
          },
          { letter: 'B', status: 'FINISHED', results: [row(1, 3, 'B Winner')], legs: null },
          { letter: 'C', status: 'PENDING', results: [], legs: null },
        ],
      }],
    });
    renderPage();

    expect(await screen.findByText('1/10 Buggy')).toBeInTheDocument();
    expect(screen.getByText('2 of 3 finals finished')).toBeInTheDocument();
    expect(screen.getByText('Overall Winner')).toBeInTheDocument();
    expect(screen.queryByText('Leg Leader')).not.toBeInTheDocument();
    expect(screen.getByText('B Winner')).toBeInTheDocument();
    expect(screen.getByText('Not run yet.')).toBeInTheDocument();
  });

  it('says so when there are no finals', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue({ eventId: 7, eventName: 'Club Round 3', classes: [] });
    renderPage();
    expect(await screen.findByText('No finals yet.')).toBeInTheDocument();
  });
});
