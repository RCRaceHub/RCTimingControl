import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LiveTimingPanel } from './LiveTimingPanel';
import { NO_SECTORS, type SectorBoard } from '@/lib/sectors';

const row = (entryId: number, position: number, driverName: string) => ({
  entryId,
  driverName,
  carNumber: String(position),
  position,
  lapsCompleted: 3,
  lastPassingTimeMs: 0,
  lastLapMs: 12000,
  bestLapMs: 11900,
  avgLapMs: 12000,
  overallFastestLapMs: 11900,
  lapsDown: 0,
  intervalLapsDown: 0,
  gapToLeaderMs: position === 1 ? null : 1500,
  gapToAheadMs: position === 1 ? null : 1500,
  totalTimeMs: 36000,
  finished: false,
  timePenaltyMs: 0,
  startPosition: null,
  flyingLap: false,
});

let sectors: SectorBoard = NO_SECTORS;

vi.mock('@/hooks/race-control/useLiveTiming', () => ({
  useLiveTiming: () => ({
    rows: [row(1, 1, 'Ann Racer'), row(2, 2, 'Bob Racer')],
    wsStatus: 'connected',
    snapshotFailed: false,
  }),
}));
vi.mock('@/hooks/race-control/useSectors', () => ({ useSectors: () => sectors }));
vi.mock('@/hooks/race-control/useEliminations', () => ({ useEliminations: () => ({ shikabane: false, deadOrder: new Map(), revivals: new Map() }) }));
vi.mock('@/hooks/race-control/useLapBarClock', () => ({ useLapBarClock: () => 0 }));
vi.mock('@/hooks/useRaceMedia', () => ({ useRaceMedia: () => new Map() }));
vi.mock('@/hooks/useBoardSettings', () => ({
  useBoardSettings: () => ({ closeGapMs: 0, lateMarginMs: 5000, hiddenRowIcons: [] }),
}));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <LiveTimingPanel raceId={9} status="RUNNING" />
    </QueryClientProvider>,
  );
}

describe('LiveTimingPanel sector columns', () => {
  it('shows no sector columns for a club with a single decoder', () => {
    sectors = NO_SECTORS;
    renderPanel();

    expect(screen.getByText('Ann Racer')).toBeInTheDocument();
    expect(screen.queryByText('S1')).not.toBeInTheDocument();
  });

  it('shows a column per sector, with a dash for a car that has no lap with every split yet', () => {
    sectors = {
      sectorCount: 3,
      overallBestMs: [4000, 4800, 6000],
      rows: [{ entryId: 1, lapNumber: 2, lastMs: [4000, 4900, 6100], bestMs: [4000, 4800, 6000] }],
    };
    renderPanel();

    expect(screen.getByText('S1')).toBeInTheDocument();
    expect(screen.getByText('S3')).toBeInTheDocument();
    expect(screen.getByText('4.000')).toBeInTheDocument();
    expect(screen.getByText('4.900')).toBeInTheDocument();
    // Bob has no sector times: his three sector cells read as a dash, not as empty cells
    const bobRow = screen.getByText('Bob Racer').closest('tr')!;
    expect(bobRow.querySelectorAll('td')).toHaveLength(screen.getByText('Ann Racer').closest('tr')!.querySelectorAll('td').length);
    expect(bobRow.textContent).toContain('—');
  });
});
