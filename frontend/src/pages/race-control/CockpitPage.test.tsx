import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { toast } from 'sonner';
import CockpitPage from './CockpitPage';

const mockUser = vi.fn();
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: mockUser() }) }));
vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));

const mockRunOrder = vi.fn();
vi.mock('@/hooks/race-control/useRunOrder', () => ({ useRunOrder: () => mockRunOrder() }));
const mockMutate = vi.fn();
vi.mock('@/hooks/race-control/useRaceStateMutations', () => ({
  useRaceStateMutations: () =>
    new Proxy({}, { get: () => ({ mutate: mockMutate, isPending: false }) }),
}));
vi.mock('./panels/LiveTimingPanel', () => ({ LiveTimingPanel: () => null }));
const mockUseStomp = vi.fn<(topic: string | null) => { data: unknown; status: string }>(() => ({ data: null, status: 'connected' }));
vi.mock('@/hooks/race-control/useStomp', () => ({ useStomp: (topic: string | null) => mockUseStomp(topic) }));
vi.mock('@/hooks/race-control/useLiveTiming', () => ({ useLiveTiming: () => ({ rows: [] }) }));
vi.mock('@/hooks/race-control/useAnnouncements', () => ({
  useAnnouncements: () => ({ playBeep: vi.fn(), setClipMap: vi.fn(), markAbandoned: vi.fn(), clearAbandoned: vi.fn() }),
}));
const mockReadiness = vi.fn<() => unknown>(() => undefined);
vi.mock('@/hooks/race-control/usePreRaceReadiness', () => ({ usePreRaceReadiness: () => ({ data: mockReadiness() }) }));
vi.mock('@/hooks/race-control/usePregeneratedClips', () => ({ usePregeneratedClips: () => undefined }));
vi.mock('@/lib/audioApi', () => ({ getAudioSettings: vi.fn().mockResolvedValue(null) }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('./panels/AudioSettingsPanel', () => ({ AudioSettingsPanel: () => null }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    getEvent: vi.fn().mockResolvedValue({
      id: 7,
      classes: [{ id: 11, racingClassId: 3, configSnapshot: { type: 'TIMED' } }],
    }),
    listRacingClasses: vi.fn().mockResolvedValue([{ id: 3, name: '13.5 Touring' }]),
    generateRounds: vi.fn().mockResolvedValue(undefined),
  },
}));

import { adminApi } from '@/lib/adminApi';

function cockpit(qc: QueryClient) {
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/race-control/event/7']}>
        <Routes>
          <Route path="/race-control/event/:eventId" element={<CockpitPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function renderCockpit() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(cockpit(qc));
  return { ...result, rerenderCockpit: () => result.rerender(cockpit(qc)) };
}

describe('CockpitPage with no races', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRunOrder.mockReturnValue({ data: [], isLoading: false, isError: false });
  });

  it('lets a race director generate the rounds', async () => {
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    renderCockpit();

    expect(screen.getByText('No races yet')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Generate Rounds' }));

    await screen.findByText('13.5 Touring');
    fireEvent.click(screen.getByRole('button', { name: 'Generate Rounds' }));

    await waitFor(() => expect(adminApi.generateRounds).toHaveBeenCalledTimes(1));
    expect(vi.mocked(adminApi.generateRounds).mock.calls[0][0]).toBe(7);
    expect(vi.mocked(adminApi.generateRounds).mock.calls[0][1].classFinalsConfigs).toEqual([
      expect.objectContaining({ eventClassId: 11 }),
    ]);
  });

  it('tells a referee someone else has to generate them', () => {
    mockUser.mockReturnValue({ roles: ['REFEREE'] });
    renderCockpit();

    expect(screen.getByText(/race director or admin needs to generate/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Generate Rounds' })).not.toBeInTheDocument();
  });

  it('shows a load failure instead of offering to generate rounds', () => {
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    mockRunOrder.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    renderCockpit();

    expect(screen.getByText(/could not load the run order/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Generate Rounds' })).not.toBeInTheDocument();
  });
});

describe('CockpitPage with the grid called', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    mockRunOrder.mockReturnValue({
      data: [{ raceId: 3, status: 'GRID', roundType: 'PRACTICE', roundNumber: 1, className: '13.5 Touring', heatNumber: 1 }],
      isLoading: false,
      isError: false,
    });
  });

  it('says drivers are called by name every interval for a staggered start', () => {
    mockReadiness.mockReturnValue({ startType: 'STAGGER', staggerIntervalSeconds: 4, gridCall: [], marshalDuty: [] });
    renderCockpit();
    expect(screen.getByTestId('start-type-note')).toHaveTextContent(/called by name every 4 seconds in grid order/i);
  });

  it('says drivers are not called one by one for a rolling start', () => {
    mockReadiness.mockReturnValue({ startType: 'ROLLING', staggerIntervalSeconds: 4, gridCall: [], marshalDuty: [] });
    renderCockpit();
    expect(screen.getByTestId('start-type-note')).toHaveTextContent(/not called one by one/i);
  });
});

describe('CockpitPage with a running race', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockReadiness.mockReturnValue(undefined);
    mockRunOrder.mockReturnValue({
      data: [{ raceId: 3, status: 'RUNNING', roundType: 'PRACTICE', roundNumber: 1, className: '13.5 Touring', heatNumber: 1 }],
      isLoading: false,
      isError: false,
    });
  });

  it('offers Finish Race to a race director', () => {
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    renderCockpit();
    expect(screen.getByRole('button', { name: 'Finish Race' })).toBeInTheDocument();
  });

  it('hides Finish Race from a referee', () => {
    mockUser.mockReturnValue({ roles: ['REFEREE'] });
    renderCockpit();
    expect(screen.queryByRole('button', { name: 'Finish Race' })).not.toBeInTheDocument();
  });
});

describe('CockpitPage jump to a race', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    mockRunOrder.mockReturnValue({
      data: [
        { raceId: 3, status: 'RUNNING', roundType: 'PRACTICE', roundNumber: 1, className: '13.5 Touring', heatNumber: 1 },
        { raceId: 4, status: 'PENDING', roundType: 'PRACTICE', roundNumber: 1, className: '13.5 Touring', heatNumber: 2 },
      ],
      isLoading: false,
      isError: false,
    });
  });

  it('shows the reason when the server refuses the jump', () => {
    mockMutate.mockImplementationOnce((_vars, options?: { onError?: (err: unknown) => void }) =>
      options?.onError?.({
        isAxiosError: true,
        response: { status: 409, data: { detail: 'Race 3 is still running' } },
      }),
    );
    renderCockpit();

    fireEvent.click(screen.getByText('Heat 2'));
    fireEvent.click(screen.getByRole('button', { name: 'Jump to this race' }));

    expect(mockMutate).toHaveBeenCalledWith({ sourceRaceId: 3, targetRaceId: 4 }, expect.anything());
    expect(toast.error).toHaveBeenCalledWith('Jump to race failed: Race 3 is still running');
  });
});

describe('CockpitPage final guard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    mockUseStomp.mockImplementation(() => ({ data: null, status: 'connected' }));
  });

  function runOrderWith(qualifierStatus: string) {
    mockRunOrder.mockReturnValue({
      data: [
        { raceId: 4, status: qualifierStatus, roundType: 'QUALIFIER', roundNumber: 1, className: '13.5 Touring', heatNumber: 1 },
        { raceId: 5, status: 'PENDING', roundType: 'FINAL', roundNumber: 1, className: '13.5 Touring', heatNumber: 1 },
      ],
      isLoading: false,
      isError: false,
    });
  }

  it('blocks Call Grid on a final while a qualifying heat is unfinished', () => {
    runOrderWith('PENDING');
    renderCockpit();
    fireEvent.click(screen.getByText(/Final/));
    expect(screen.getByRole('button', { name: 'Call Grid' })).toBeDisabled();
    expect(screen.getByText(/1 qualifying heat for 13.5 Touring still to finish/)).toBeInTheDocument();
  });
});

describe('CockpitPage bump-up alert', () => {
  const bumpUpTopic = '/topic/race/5/bump-up-alert';

  function runOrderWith(status: string, roundType: string) {
    mockRunOrder.mockReturnValue({
      data: [{ raceId: 5, status, roundType, roundNumber: 1, className: '13.5 Touring', heatNumber: 2 }],
      isLoading: false,
      isError: false,
    });
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    mockUseStomp.mockImplementation(() => ({ data: null, status: 'connected' }));
  });

  it('listens while a final is still running, before the server sends the alert', () => {
    runOrderWith('RUNNING', 'FINAL');
    renderCockpit();
    expect(mockUseStomp).toHaveBeenCalledWith(bumpUpTopic);
  });

  it('does not listen for a race that is not a final', () => {
    runOrderWith('RUNNING', 'QUALIFIER');
    renderCockpit();
    expect(mockUseStomp).not.toHaveBeenCalledWith(bumpUpTopic);
  });

  it('toasts a bump-up once, even when the frame comes back again', async () => {
    runOrderWith('FINISHED', 'FINAL');
    // A fresh object on every render stands in for the frame reappearing when the final is selected again
    mockUseStomp.mockImplementation((topic) => ({
      data: topic === bumpUpTopic ? { finishedRaceId: 5, promotedEntryIds: [21, 22] } : null,
      status: 'connected',
    }));
    const { rerenderCockpit } = renderCockpit();
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    rerenderCockpit();
    rerenderCockpit();
    expect(toast.success).toHaveBeenCalledTimes(1);
    expect(vi.mocked(toast.success).mock.calls[0][0]).toMatch(/2 driver\(s\) promoted/);
  });
});
