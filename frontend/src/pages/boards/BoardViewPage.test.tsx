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
  getRaceMedia: vi.fn(),
}));

import { getBoardView } from '@/lib/boardViewsApi';
import { getBoardLiveTiming, getLiveQualifying, getNowNext, getRaceMedia, getTicker } from '@/lib/boardsApi';

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
  vi.mocked(getRaceMedia).mockResolvedValue([]);
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

  it('draws driver cards with headshots and sponsors, and a car photo on the timing table', async () => {
    vi.mocked(getBoardView).mockResolvedValue({
      id: 2, slug: 'pit', name: 'Pit', columns: 1,
      panels: [{ type: 'LIVE_TIMING', top: null, span: 1 }, { type: 'DRIVER_CARDS', top: null, span: 1 }],
    });
    vi.mocked(getNowNext).mockResolvedValue(nowNext({ currentRace: race }));
    vi.mocked(getBoardLiveTiming).mockResolvedValue([
      {
        entryId: 5, driverName: 'Ann Racer', carNumber: '3', position: 1, lapsCompleted: 2, lastPassingTimeMs: 1,
        lastLapMs: 30000, bestLapMs: 30000, avgLapMs: 30000, overallFastestLapMs: 30000, lapsDown: 0,
        intervalLapsDown: 0, gapToLeaderMs: null, gapToAheadMs: null, totalTimeMs: 60000,
      },
    ]);
    vi.mocked(getRaceMedia).mockResolvedValue([
      { entryId: 5, headshotUrl: '/storage/h.png', carPhotoUrl: '/storage/c.png', sponsors: ['Acme Tyres', 'Bolt Motors'], carColours: [] },
    ]);

    renderView();

    expect(await screen.findByText('Acme Tyres · Bolt Motors')).toBeInTheDocument();
    expect(screen.getByTestId('view-driver-cards')).toBeInTheDocument();
    expect((await screen.findAllByTestId('car-thumb')).length).toBeGreaterThan(0);
  });

  it('says so when there is no view at that address', async () => {
    vi.mocked(getBoardView).mockRejectedValue({ response: { status: 404 } });
    vi.mocked(getNowNext).mockResolvedValue(nowNext({}));

    renderView('/boards/view/nope');

    expect(await screen.findByText('There is no board view called "nope".')).toBeInTheDocument();
  });
});
