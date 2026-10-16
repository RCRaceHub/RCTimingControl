import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { QualifyingTablePanel } from './QualifyingTablePanel';

const mockGet = vi.fn();
const mockSeed = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getQualifying: (...args: unknown[]) => mockGet(...args),
  seedFinalsFromQualifying: (...args: unknown[]) => mockSeed(...args),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function renderPanel(canSeed = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <QualifyingTablePanel eventId={1} eventClassId={7} canSeed={canSeed} />
    </QueryClientProvider>,
  );
}

const table = (finalsStarted = false, heatsFinished = 3) => [
  {
    eventClassId: 7,
    className: '13.5 Touring',
    heatsFinished,
    heatsTotal: 3,
    finalsStarted,
    rows: [
      { position: 1, entryId: 11, driverName: 'Ann Racer', lapsCompleted: 21, bestLapMs: 12345, points: null, rounds: [] },
      { position: 2, entryId: 12, driverName: 'Bob Racer', lapsCompleted: 20, bestLapMs: null, points: null, rounds: [] },
    ],
  },
];

describe('QualifyingTablePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSeed.mockResolvedValue(undefined);
  });

  it('shows every driver in qualifying order and how many heats are done', async () => {
    mockGet.mockResolvedValue(table());
    renderPanel();
    await waitFor(() => expect(screen.getByText('Ann Racer')).toBeInTheDocument());
    expect(screen.getByText('Bob Racer')).toBeInTheDocument();
    expect(screen.getByText(/3 of 3 qualifying heats finished/)).toBeInTheDocument();
  });

  it('seeds the finals for the class from the table', async () => {
    mockGet.mockResolvedValue(table());
    renderPanel();
    await waitFor(() => screen.getByText('Ann Racer'));
    fireEvent.click(screen.getByRole('button', { name: 'Seed finals from this order' }));
    await waitFor(() => expect(mockSeed).toHaveBeenCalledWith(1, 7));
  });

  it('cannot seed while a qualifying heat is unfinished', async () => {
    mockGet.mockResolvedValue(table(false, 2));
    renderPanel();
    await waitFor(() => screen.getByText('Ann Racer'));
    expect(screen.getByRole('button', { name: 'Seed finals from this order' })).toBeDisabled();
  });

  it('cannot seed once a final has started', async () => {
    mockGet.mockResolvedValue(table(true));
    renderPanel();
    await waitFor(() => screen.getByText('Ann Racer'));
    expect(screen.getByRole('button', { name: 'Seed finals from this order' })).toBeDisabled();
  });
});
