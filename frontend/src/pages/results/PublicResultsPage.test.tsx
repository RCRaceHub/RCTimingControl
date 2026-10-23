import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PublicResultsPage from './PublicResultsPage';
import { usePublicResultSnapshot } from '@/hooks/race-control/usePublicResultSnapshot';

vi.mock('@/hooks/race-control/usePublicResultSnapshot', () => ({
  usePublicResultSnapshot: vi.fn(),
}));

function snapshot(lapHistory: unknown[]) {
  return {
    raceId: 5,
    raceLabel: 'Buggy Q1 Heat 1',
    finishedAt: '2026-10-17T10:00:00Z',
    clubBranding: null,
    positions: [
      { position: 1, entryId: 10, driverName: 'Ann Racer', carNumber: '1', lapsCompleted: 2, totalTimeMs: 41_000, bestLapMs: 20_000, gapToLeaderMs: null },
      { position: 2, entryId: 11, driverName: 'Bob Racer', carNumber: '2', lapsCompleted: 2, totalTimeMs: 43_000, bestLapMs: 21_000, gapToLeaderMs: 2_000 },
    ],
    lapHistory,
  };
}

function renderPage(lapHistory: unknown[]) {
  vi.mocked(usePublicResultSnapshot).mockReturnValue({
    data: snapshot(lapHistory),
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof usePublicResultSnapshot>);
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/results/5']}>
        <Routes>
          <Route path="/results/:raceId" element={<PublicResultsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PublicResultsPage', () => {
  it('draws the position chart when the race has laps', () => {
    renderPage([
      { lapNumber: 1, entryId: 10, position: 2, lapTimeMs: 21_000 },
      { lapNumber: 2, entryId: 10, position: 1, lapTimeMs: 20_000 },
      { lapNumber: 1, entryId: 11, position: 1, lapTimeMs: 22_000 },
      { lapNumber: 2, entryId: 11, position: 2, lapTimeMs: 21_000 },
    ]);
    expect(screen.getByRole('img', { name: 'Position by lap' })).toBeInTheDocument();
  });

  it('leaves the chart out when no laps were recorded', () => {
    renderPage([]);
    expect(screen.queryByRole('img', { name: 'Position by lap' })).toBeNull();
  });
});
