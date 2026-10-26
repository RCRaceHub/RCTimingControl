import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ResultsPage from './ResultsPage';
import ResultsRedirect from './ResultsRedirect';

vi.mock('@/lib/boardsApi', () => ({
  getQualifyingBoard: vi.fn(),
  getFinalsBoard: vi.fn(),
  getPodiumBoard: vi.fn(),
}));
vi.mock('@/lib/raceControlApi', () => ({
  getEventSchedule: vi.fn(),
  getPublicChampionshipInfo: vi.fn().mockResolvedValue({ name: 'Club Series', classes: [], splits: [] }),
  getPublicChampionshipStandings: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/lib/api', () => ({
  default: {},
  getAboutInfo: vi.fn().mockResolvedValue({ version: '1', buildTime: '', addresses: [] }),
}));

import type { FinalsBoardDto } from '@/lib/boardsApi';
import { getFinalsBoard, getPodiumBoard, getQualifyingBoard } from '@/lib/boardsApi';
import { getEventSchedule } from '@/lib/raceControlApi';

function renderAt(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/results" element={<ResultsPage />} />
          <Route path="/finals" element={<ResultsRedirect tab="finals" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const finalsWith = (finished: number): FinalsBoardDto => ({
  eventId: 7,
  eventName: 'Club Round 3',
  classes: finished === 0 ? [] : [{
    eventClassId: 1, className: 'Buggy', finalsFinished: finished, finalsTotal: 1,
    finals: [{ letter: 'A', status: 'FINISHED', abandoned: false, legs: null, results: [] }],
  }],
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getQualifyingBoard).mockResolvedValue({ eventId: 7, eventName: 'Club Round 3', tables: [] });
  vi.mocked(getPodiumBoard).mockResolvedValue({ eventId: 7, eventName: 'Club Round 3', classes: [] });
  vi.mocked(getEventSchedule).mockResolvedValue([
    { id: 7, name: 'Club Round 3', eventDate: '2026-10-24', finishedRaces: [], championshipId: null },
  ]);
});

describe('ResultsPage', () => {
  it('opens on Qualifying until a final has finished', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(0));
    renderAt('/results?event=7');

    expect(await screen.findByText('No qualifying results yet.')).toBeInTheDocument();
    expect(screen.getByText('Club Round 3')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Qualifying' })).toHaveAttribute('aria-selected', 'true');
  });

  it('opens on Finals once a final has finished', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(1));
    renderAt('/results?event=7');

    expect(await screen.findByText('1 of 1 finals finished')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Finals' })).toHaveAttribute('aria-selected', 'true');
  });

  it('switches tabs and hides the Championship tab when the event has none', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(0));
    renderAt('/results?event=7');

    fireEvent.mouseDown(await screen.findByRole('tab', { name: 'Podium' }));
    expect(await screen.findByText('No finals have finished yet.')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Championship' })).not.toBeInTheDocument();
  });

  it('shows the Championship tab for an event in a championship', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(0));
    vi.mocked(getEventSchedule).mockResolvedValue([
      { id: 7, name: 'Club Round 3', eventDate: '2026-10-24', finishedRaces: [], championshipId: 3 },
    ]);
    renderAt('/results?event=7&tab=championship');

    expect(await screen.findByText('No results have counted yet.')).toBeInTheDocument();
  });

  it('ignores an unknown tab and a championship tab the event does not have', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(0));
    renderAt('/results?event=7&tab=championship');

    expect(await screen.findByText('No qualifying results yet.')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Qualifying' })).toHaveAttribute('aria-selected', 'true');
  });

  it('redirects the old /finals address to its tab, keeping the event', async () => {
    vi.mocked(getFinalsBoard).mockResolvedValue(finalsWith(0));
    renderAt('/finals?event=7');

    expect(await screen.findByRole('tab', { name: 'Finals' })).toHaveAttribute('aria-selected', 'true');
    expect(getFinalsBoard).toHaveBeenCalledWith(7);
  });
});
