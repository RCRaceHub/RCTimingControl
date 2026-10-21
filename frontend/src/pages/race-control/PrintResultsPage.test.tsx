import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrintResultsPage from './PrintResultsPage';
import { getRaceHistory, getRaceResult } from '@/lib/raceControlApi';

vi.mock('@/lib/raceControlApi', () => ({
  getRaceResult: vi.fn(),
  getRaceHistory: vi.fn(),
}));

const snapshot = {
  raceId: 5,
  raceLabel: 'Buggy Q1 Heat 1',
  finishedAt: '2026-10-17T10:00:00Z',
  clubBranding: null,
  positions: [
    { position: 1, entryId: 10, driverName: 'Ann Racer', carNumber: '1', lapsCompleted: 2, totalTimeMs: 41_000, bestLapMs: 20_000, gapToLeaderMs: null },
    { position: 2, entryId: 11, driverName: 'Bob Racer', carNumber: '2', lapsCompleted: 2, totalTimeMs: 43_000, bestLapMs: 21_000, gapToLeaderMs: 2_000 },
  ],
  lapHistory: [
    { lapNumber: 1, entryId: 10, position: 1, lapTimeMs: 21_000 },
    { lapNumber: 2, entryId: 10, position: 1, lapTimeMs: 20_000 },
    { lapNumber: 1, entryId: 11, position: 2, lapTimeMs: 22_000 },
    { lapNumber: 2, entryId: 11, position: 2, lapTimeMs: 21_000 },
  ],
};

const raceResult = {
  eventId: 1,
  eventName: 'Autumn Club Day',
  eventDate: '2026-10-17',
  races: [
    {
      raceId: 5,
      eventClassId: 2,
      className: 'Buggy',
      roundNumber: 1,
      roundType: 'QUALIFIER',
      heatNumber: 1,
      finalLetter: null,
      result: snapshot,
      startPositions: { '10': 2, '11': 1 },
    },
  ],
};

function renderPage(url = '/results/5') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/results/:raceId" element={<PrintResultsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PrintResultsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getRaceResult).mockResolvedValue(raceResult as never);
    vi.mocked(getRaceHistory).mockResolvedValue([]);
  });

  it('shows each driver\'s average lap and leaves the lap sheet off by default', async () => {
    renderPage();

    expect(await screen.findByText('Ann Racer')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Avg Lap' })).toBeInTheDocument();
    expect(screen.getByText('20.500')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Lap times' })).not.toBeInTheDocument();
  });

  it('shows where each driver started and the event it belongs to', async () => {
    renderPage();

    expect(await screen.findByText('Ann Racer')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Start' })).toBeInTheDocument();
    const annRow = screen.getByText('Ann Racer').closest('tr') as HTMLElement;
    // Pos, Driver, Car, Start: Ann started second
    expect(within(annRow).getAllByRole('cell')[3]).toHaveTextContent('2');
    const bobRow = screen.getByText('Bob Racer').closest('tr') as HTMLElement;
    expect(within(bobRow).getAllByRole('cell')[3]).toHaveTextContent('1');
    expect(screen.getByText(/Autumn Club Day/)).toBeInTheDocument();
  });

  it('draws the position chart unless it is switched off in the address', async () => {
    renderPage();
    expect(await screen.findByRole('img', { name: 'Position by lap' })).toBeInTheDocument();
  });

  it('leaves the position chart off when the address says chart=0', async () => {
    renderPage('/results/5?chart=0');
    expect(await screen.findByText('Ann Racer')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Position by lap' })).not.toBeInTheDocument();
  });

  it('adds the lap-by-lap sheet when asked for it in the address', async () => {
    renderPage('/results/5?laps=1');

    expect(await screen.findByRole('region', { name: 'Lap times' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Include lap-by-lap times' })).toBeChecked();
  });

  it('lists the penalties given in the heat', async () => {
    vi.mocked(getRaceHistory).mockResolvedValue([
      { at: '2026-10-17T10:01:00Z', kind: 'PENALTY', actor: 'Ref', driver: 'Bob Racer', summary: '1 lap penalty (cutting the track)' },
      { at: '2026-10-17T10:00:00Z', kind: 'LIFECYCLE', actor: null, driver: null, summary: 'Race finished' },
    ]);

    renderPage();

    expect(await screen.findByText(/Bob Racer: 1 lap penalty/)).toBeInTheDocument();
    expect(screen.queryByText(/Race finished/)).not.toBeInTheDocument();
  });
});
