import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrintEventResultsPage from './PrintEventResultsPage';
import { getEventResults, getQualifying } from '@/lib/raceControlApi';

vi.mock('@/lib/raceControlApi', () => ({
  getEventResults: vi.fn(),
  getQualifying: vi.fn(),
}));

function heat(raceId: number, eventClassId: number, className: string, over: Record<string, unknown> = {}) {
  return {
    raceId,
    eventClassId,
    className,
    roundNumber: 1,
    roundType: 'QUALIFIER',
    heatNumber: 1,
    finalLetter: null,
    startPositions: { '10': 1 },
    result: {
      raceId,
      raceLabel: `${className} race ${raceId}`,
      finishedAt: '2026-10-17T10:00:00Z',
      clubBranding: { clubName: 'Test MCC', logoUrl: null },
      positions: [
        { position: 1, entryId: 10, driverName: `Driver ${raceId}`, carNumber: '1', lapsCompleted: 2, totalTimeMs: 41_000, bestLapMs: 20_000, gapToLeaderMs: null },
      ],
      lapHistory: [],
    },
    ...over,
  };
}

const results = {
  eventId: 3,
  eventName: 'Autumn Club Day',
  eventDate: '2026-10-17',
  races: [
    heat(1, 7, 'Buggy'),
    heat(2, 7, 'Buggy', { roundNumber: 2 }),
    heat(3, 7, 'Buggy', { roundType: 'FINAL', roundNumber: 3, finalLetter: 'B' }),
    heat(4, 7, 'Buggy', { roundType: 'FINAL', roundNumber: 3, finalLetter: 'A' }),
    heat(5, 8, 'Truck'),
  ],
};

function renderPage(search: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/race-control/print/event/3${search}`]}>
        <Routes>
          <Route path="/race-control/print/event/:eventId" element={<PrintEventResultsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PrintEventResultsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getEventResults).mockResolvedValue(results as never);
    vi.mocked(getQualifying).mockResolvedValue([
      {
        eventClassId: 7, className: 'Buggy', heatsFinished: 2, heatsTotal: 2, finalsStarted: true,
        rows: [{ position: 1, entryId: 10, driverName: 'Ann Racer', carNumber: '1', lapsCompleted: 4, bestLapMs: 20_000, points: null, rounds: [] }],
      },
    ] as never);
  });

  it('prints one round with a page per class that ran it', async () => {
    renderPage('?view=round&type=QUALIFIER&round=2');

    const buggy = await screen.findByRole('article', { name: 'Buggy' });
    expect(within(buggy).getByText('Driver 2')).toBeInTheDocument();
    expect(within(buggy).queryByText('Driver 1')).not.toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Truck' })).not.toBeInTheDocument();
  });

  it('prints the finals with the A final first', async () => {
    renderPage('?view=finals');

    const buggy = await screen.findByRole('article', { name: 'Buggy' });
    const titles = within(buggy).getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(titles).toEqual(['Final A', 'Final B']);
  });

  it('builds a meeting pack from the parts asked for', async () => {
    renderPage('?view=pack&parts=standings,finals');

    const buggy = await screen.findByRole('article', { name: 'Buggy' });
    expect(await within(buggy).findByRole('region', { name: 'Qualifying standings' })).toBeInTheDocument();
    expect(within(buggy).getByText('Final A')).toBeInTheDocument();
    expect(within(buggy).queryByText('Qualifier 1')).not.toBeInTheDocument();
  });

  it('says so when nothing has finished', async () => {
    vi.mocked(getEventResults).mockResolvedValue({ ...results, races: [] } as never);

    renderPage('?view=finals');

    expect(await screen.findByText(/Nothing has finished yet/)).toBeInTheDocument();
  });
});
