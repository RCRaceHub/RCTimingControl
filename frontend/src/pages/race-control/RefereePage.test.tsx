import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
const NO_ROWS: never[] = [];
vi.mock('@/hooks/race-control/useLiveTiming', () => ({ useLiveTiming: () => ({ rows: NO_ROWS }) }));
const mockRaceClock = vi.fn();
vi.mock('@/hooks/race-control/useRaceClock', () => ({ useRaceClock: () => mockRaceClock() }));
vi.mock('@/hooks/race-control/useJumpStarts', () => ({ useJumpStarts: () => [] }));
vi.mock('./panels/LiveTimingPanel', () => ({ LiveTimingPanel: () => null }));
vi.mock('./panels/RunOrderPanel', () => ({
  RunOrderPanel: (props: { onSelect: (id: number) => void }) => (
    <button onClick={() => props.onSelect(5)}>pick race 5</button>
  ),
}));
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
  const ui = (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/race-control/event/7/referee']}>
        <Routes>
          <Route path="/race-control/event/:eventId/referee" element={<RefereePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  const view = render(ui);
  return { ...view, rerenderPage: () => view.rerender(ui) };
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

  it('moves to the next race when it goes live', async () => {
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'RUNNING' }, { raceId: 6, status: 'PENDING' }] });
    const view = renderReferee();
    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(5));

    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'FINISHED' }, { raceId: 6, status: 'RUNNING' }] });
    view.rerenderPage();

    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(6));
    expect(screen.getByText('Following the live race')).toBeInTheDocument();
  });

  it('stays on a race picked by hand until asked to follow the live race again', async () => {
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'FINISHED' }, { raceId: 6, status: 'RUNNING' }] });
    const view = renderReferee();
    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(6));

    fireEvent.click(screen.getByText('pick race 5'));
    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(5));
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'FINISHED' }, { raceId: 7, status: 'RUNNING' }] });
    view.rerenderPage();
    expect(getRaceEntries).toHaveBeenLastCalledWith(5);

    fireEvent.click(screen.getByText('Follow the live race'));
    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(7));
  });

  it('stays on the last race between races, and keeps following when the live race is clicked', async () => {
    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'RUNNING' }, { raceId: 6, status: 'PENDING' }] });
    const view = renderReferee();
    await waitFor(() => expect(getRaceEntries).toHaveBeenLastCalledWith(5));

    mockRunOrder.mockReturnValue({ data: [{ raceId: 5, status: 'FINISHED' }, { raceId: 6, status: 'PENDING' }] });
    view.rerenderPage();
    expect(getRaceEntries).toHaveBeenLastCalledWith(5);
    expect(await screen.findByText('No race is live; showing the last one')).toBeInTheDocument();
  });
});
