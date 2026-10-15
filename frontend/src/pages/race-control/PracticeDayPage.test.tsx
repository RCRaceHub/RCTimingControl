import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PracticeDayPage } from './PracticeDayPage';

vi.mock('@/lib/practiceApi', () => ({ getPracticeDay: vi.fn() }));

import * as practiceApi from '@/lib/practiceApi';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PracticeDayPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PracticeDayPage', () => {
  it('shows each driver with their totals and the average of the laps', async () => {
    vi.mocked(practiceApi.getPracticeDay).mockResolvedValue({
      date: '2026-10-14',
      bestLapN: 2,
      sessions: [{ id: 1, name: 'Morning' }, { id: 2, name: 'Afternoon' }] as never,
      rows: [
        { competitorId: 7, name: 'Alex', transponderNumbers: '1234', sessions: 2, runs: 3, laps: 20,
          bestLapMs: 29000, bestConsecutiveNMs: 60000 },
        { competitorId: null, name: null, transponderNumbers: '9999', sessions: 1, runs: 1, laps: 4,
          bestLapMs: 31000, bestConsecutiveNMs: null },
      ],
    });

    renderPage();

    expect(await screen.findByText('Alex')).toBeInTheDocument();
    expect(screen.getByText('30.000 avg')).toBeInTheDocument();
    expect(screen.getByText('Unknown')).toBeInTheDocument();
    expect(screen.getByText(/Morning, Afternoon/)).toBeInTheDocument();
  });

  it('says so when no session started that day', async () => {
    vi.mocked(practiceApi.getPracticeDay).mockResolvedValue({
      date: '2026-10-14', bestLapN: 5, sessions: [], rows: [],
    });

    renderPage();

    expect(await screen.findByText(/No practice sessions started on this day/)).toBeInTheDocument();
  });
});
