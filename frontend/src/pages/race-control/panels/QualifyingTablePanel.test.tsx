import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { QualifyingTablePanel } from './QualifyingTablePanel';

const mockGet = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getQualifying: (...args: unknown[]) => mockGet(...args),
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <QualifyingTablePanel eventId={1} eventClassId={7} />
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
  });

  it('shows every driver in qualifying order and how many heats are done', async () => {
    mockGet.mockResolvedValue(table());
    renderPanel();
    await waitFor(() => expect(screen.getByText('Ann Racer')).toBeInTheDocument());
    expect(screen.getByText('Bob Racer')).toBeInTheDocument();
    expect(screen.getByText(/3 of 3 qualifying heats finished/)).toBeInTheDocument();
  });
});
