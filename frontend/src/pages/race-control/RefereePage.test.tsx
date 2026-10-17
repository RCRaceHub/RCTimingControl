import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import RefereePage from './RefereePage';

vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));

const mockRunOrder = vi.fn();
vi.mock('@/hooks/race-control/useRunOrder', () => ({ useRunOrder: () => mockRunOrder() }));
vi.mock('@/hooks/race-control/useRaceStateMutations', () => ({
  useRaceStateMutations: () =>
    new Proxy({}, { get: () => ({ mutate: vi.fn(), isPending: false }) }),
}));
// Live timing is empty once a race has finished
vi.mock('@/hooks/race-control/useLiveTiming', () => ({ useLiveTiming: () => ({ rows: [] }) }));
const mockRaceClock = vi.fn();
vi.mock('@/hooks/race-control/useRaceClock', () => ({ useRaceClock: () => mockRaceClock() }));
vi.mock('./referee/useProximityAlerts', () => ({ useProximityAlerts: () => new Set<number>() }));
vi.mock('./panels/LiveTimingPanel', () => ({ LiveTimingPanel: () => null }));
vi.mock('./panels/RunOrderPanel', () => ({ RunOrderPanel: () => null }));
vi.mock('./panels/RaceHistoryPanel', () => ({ RaceHistoryPanel: () => null }));

type Driver = { entryId: number; driverName: string };
const penaltyDrivers = vi.fn<(drivers: Driver[]) => void>();
const incidentDrivers = vi.fn<(drivers: Driver[]) => void>();
vi.mock('./dialogs/PenaltyDialog', () => ({
  PenaltyDialog: (props: { drivers: Driver[] }) => {
    penaltyDrivers(props.drivers);
    return null;
  },
}));
vi.mock('./dialogs/IncidentDialog', () => ({
  IncidentDialog: (props: { drivers: Driver[] }) => {
    incidentDrivers(props.drivers);
    return null;
  },
}));

vi.mock('@/lib/raceControlApi', () => ({
  getRaceEntries: vi.fn().mockResolvedValue([
    { entryId: 11, racerName: 'Alex Rowe', carNumber: null },
    { entryId: 12, racerName: 'Sam Ito', carNumber: null },
  ]),
}));

import { getRaceEntries } from '@/lib/raceControlApi';

function renderReferee() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/race-control/event/7/referee']}>
        <Routes>
          <Route path="/race-control/event/:eventId/referee" element={<RefereePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const EXPECTED = [
  { entryId: 11, driverName: 'Alex Rowe', carNumber: null },
  { entryId: 12, driverName: 'Sam Ito', carNumber: null },
];

describe('RefereePage driver list (#107)', () => {
  beforeEach(() => {
    mockRaceClock.mockReturnValue({ clock: undefined, readAt: 0 });
    penaltyDrivers.mockClear();
    incidentDrivers.mockClear();
    vi.mocked(getRaceEntries).mockClear();
  });

  it('lists the race entries for a finished race even though live timing is empty', async () => {
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'FINISHED' }] });

    renderReferee();

    await waitFor(() => expect(penaltyDrivers).toHaveBeenLastCalledWith(EXPECTED));
    expect(incidentDrivers).toHaveBeenLastCalledWith(EXPECTED);
    expect(getRaceEntries).toHaveBeenCalledWith(5);
  });

  it('does not ask for entries until a race is selected', async () => {
    mockRunOrder.mockReturnValue({ data: [] });

    renderReferee();

    await waitFor(() => expect(penaltyDrivers).toHaveBeenCalled());
    expect(penaltyDrivers).toHaveBeenLastCalledWith([]);
    expect(getRaceEntries).not.toHaveBeenCalled();
  });

  it('shows the time left of the race in progress', async () => {
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'RUNNING' }] });
    mockRaceClock.mockReturnValue({
      clock: {
        raceId: 5, status: 'RUNNING', elapsedMs: 60_000, durationMs: 300_000, remainingMs: 240_000, running: false,
      },
      readAt: Date.now(),
    });

    renderReferee();

    expect(await screen.findByRole('timer')).toHaveTextContent('Time left4:00');
  });
});
