import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import BoardViewPage from './BoardViewPage';
import type { NowNextDto } from '@/lib/boardsApi';

vi.mock('@/hooks/race-control/useStomp', () => ({
  useStomp: () => ({ data: null, status: 'connected' }),
}));

vi.mock('@/lib/boardViewsApi', () => ({
  getBoardView: vi.fn(),
}));

vi.mock('@/lib/boardsApi', () => ({
  getNowNext: vi.fn(),
  getBoardLiveTiming: vi.fn(),
  getTicker: vi.fn(),
  getLiveQualifying: vi.fn(),
  getResultsBoard: vi.fn(),
}));

import { getBoardView } from '@/lib/boardViewsApi';
import { getBoardLiveTiming, getLiveQualifying, getNowNext, getTicker } from '@/lib/boardsApi';

const race = {
  raceId: 12,
  label: 'Qualifying 1 — Stock Buggy — Heat 2',
  roundType: 'QUALIFIER',
  roundNumber: 1,
  className: 'Stock Buggy',
  heatNumber: 2,
  finalLetter: null,
  status: 'RUNNING',
} as const;

function nowNext(overrides: Partial<NowNextDto>): NowNextDto {
  return { eventId: 7, eventName: 'Club Round 3', currentRace: null, nextRace: null, lastCompletedRace: null, ...overrides };
}

function renderView(path = '/boards/view/pit') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/boards/view/:slug" element={<BoardViewPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getBoardLiveTiming).mockResolvedValue([]);
  vi.mocked(getLiveQualifying).mockResolvedValue({
    eventId: 7, eventName: 'Club Round 3', phase: 'NONE', raceId: null, raceLabel: null, className: null, rows: [],
  });
});

describe('BoardViewPage', () => {
  it('draws each panel of the view in order', async () => {
    vi.mocked(getBoardView).mockResolvedValue({
      id: 1, slug: 'pit', name: 'Pit', columns: 2,
      panels: [
        { type: 'LIVE_TIMING', top: null, span: 2 },
        { type: 'MARSHALS', top: null, span: 1 },
        { type: 'LIVE_TOP', top: 10, span: 1 },
      ],
    });
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race }));
    vi.mocked(getTicker).mockResolvedValue({
      nextRace: null, nextDrivers: [], marshals: [], currentMarshals: ['Sam Marshal'], marshalsPending: false, topQualifiers: [],
    });

    renderView();

    expect(await screen.findByText(race.label)).toBeInTheDocument();
    expect(await screen.findByText('Sam Marshal')).toBeInTheDocument();
    expect(await screen.findByText('No qualifying table yet.')).toBeInTheDocument();
  });

  it('says so when there is no view at that address', async () => {
    vi.mocked(getBoardView).mockRejectedValue({ response: { status: 404 } });
    vi.mocked(getNowNext).mockResolvedValue(nowNext({}));

    renderView('/boards/view/nope');

    expect(await screen.findByText('There is no board view called "nope".')).toBeInTheDocument();
  });
});
