import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import PrintCentrePage from './PrintCentrePage';
import type { EventResultsDto, EventResultsRace } from '@/lib/raceControlApi';

vi.mock('@/lib/raceControlApi', () => ({ getEventResults: vi.fn() }));
vi.mock('@/lib/practiceApi', () => ({ listSessions: vi.fn() }));

import { getEventResults } from '@/lib/raceControlApi';
import { listSessions } from '@/lib/practiceApi';

const race = (raceId: number, roundType: EventResultsRace['roundType'], roundNumber: number, heatNumber: number, finalLetter: string | null) =>
  ({ raceId, eventClassId: 1, className: 'Buggy', roundType, roundNumber, heatNumber, finalLetter } as EventResultsRace);

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/race-control/event/7/print']}>
        <Routes>
          <Route path="/race-control/event/:eventId/print" element={<PrintCentrePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getEventResults).mockResolvedValue({
    eventId: 7, eventName: 'Club Round 3', eventDate: '2026-10-24',
    races: [race(1, 'QUALIFIER', 1, 1, null), race(2, 'QUALIFIER', 1, 2, null), race(3, 'FINAL', 3, 1, 'A')],
  } as EventResultsDto);
  vi.mocked(listSessions).mockResolvedValue([
    { id: 5, name: 'Morning practice', eventId: 7 },
    { id: 6, name: 'Other meeting', eventId: 9 },
  ] as Awaited<ReturnType<typeof listSessions>>);
});

describe('PrintCentrePage', () => {
  it('links every finished heat and final to its sheet', async () => {
    renderPage();

    expect(await screen.findByRole('link', { name: 'Buggy heat 2' })).toHaveAttribute('href', '/race-control/event/7/results/2');
    expect(screen.getByRole('link', { name: 'Buggy final A' })).toHaveAttribute('href', '/race-control/event/7/results/3');
    expect(screen.getByRole('link', { name: 'Qualifier 1' })).toBeInTheDocument();
  });

  it('lists only this event\'s practice sessions, with the day view', async () => {
    renderPage();

    expect(await screen.findByRole('link', { name: 'Morning practice' })).toHaveAttribute('href', '/race-control/practice/5/print');
    expect(screen.queryByRole('link', { name: 'Other meeting' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Practice day view' })).toBeInTheDocument();
  });
});
