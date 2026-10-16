import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import QualifyingStandingsPage from './QualifyingStandingsPage';

vi.mock('@/lib/boardsApi', () => ({ getQualifyingBoard: vi.fn() }));
vi.mock('@/lib/api', () => ({
  default: {},
  getAboutInfo: vi.fn().mockResolvedValue({ version: '1', buildTime: '', addresses: [] }),
}));

import { getQualifyingBoard } from '@/lib/boardsApi';

function renderPage(path = '/qualifying?event=7') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <QualifyingStandingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('QualifyingStandingsPage', () => {
  it('shows each class with its drivers in order, and points when the class ranks by them', async () => {
    vi.mocked(getQualifyingBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      tables: [
        {
          eventClassId: 1,
          className: '1/10 Buggy',
          heatsFinished: 2,
          heatsTotal: 4,
          finalsStarted: false,
          rows: [
            { position: 1, entryId: 10, driverName: 'Fast Freddie', lapsCompleted: 12, bestLapMs: 19_000, points: 3,
              rounds: [{ place: 1, counted: true }, { place: 2, counted: true }, { place: 4, counted: false }] },
            { position: 2, entryId: 11, driverName: 'A. L.', lapsCompleted: 10, bestLapMs: null, points: 7,
              rounds: [{ place: 3, counted: true }, { place: 4, counted: true }, { place: 5, counted: false }] },
          ],
        },
      ],
    });

    renderPage();

    expect(await screen.findByText('Fast Freddie')).toBeInTheDocument();
    expect(screen.getByText('A. L.')).toBeInTheDocument();
    expect(screen.getByText('2 of 4 heats finished')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Points' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'R3' })).toBeInTheDocument();
    // Round 3 doesn't count for Freddie, so its place is struck through
    const dropped = screen.getAllByText('(dropped)', { exact: false });
    expect(dropped).toHaveLength(2);
    expect(dropped[0].closest('del')).toHaveTextContent('4');
    expect(getQualifyingBoard).toHaveBeenCalledWith(7);
  });

  it('leaves out the points column for a class ranked by laps', async () => {
    vi.mocked(getQualifyingBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      tables: [
        {
          eventClassId: 1,
          className: 'Touring',
          heatsFinished: 1,
          heatsTotal: 1,
          finalsStarted: false,
          rows: [{ position: 1, entryId: 10, driverName: 'Solo', lapsCompleted: 9, bestLapMs: 20_000, points: null, rounds: [] }],
        },
      ],
    });

    renderPage('/qualifying');

    expect(await screen.findByText('Solo')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Points' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'R1' })).not.toBeInTheDocument();
    expect(getQualifyingBoard).toHaveBeenCalledWith(null);
  });

  it('says so when nothing has been run', async () => {
    vi.mocked(getQualifyingBoard).mockResolvedValue({ eventId: null, eventName: null, tables: [] });

    renderPage();

    expect(await screen.findByText('No qualifying results yet.')).toBeInTheDocument();
  });
});
