import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PracticeSessionPage } from './PracticeSessionPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

// Mock practiceApi
vi.mock('@/lib/practiceApi', () => ({
  getSession: vi.fn(),
  startSession: vi.fn(),
  stopSession: vi.fn(),
  getSnapshot: vi.fn().mockResolvedValue([]),
}));

// Mock usePracticeTiming (avoids double STOMP client creation in tests)
const mockUsePracticeTiming = vi.fn();
vi.mock('@/hooks/race-control/usePracticeTiming', () => ({
  usePracticeTiming: () => mockUsePracticeTiming(),
}));

import * as practiceApi from '@/lib/practiceApi';
import { AxiosError } from 'axios';
import { toast } from 'sonner';

const idleSession = {
  id: 42,
  name: 'Sunday Practice',
  eventId: null,
  eventName: null,
  status: 'IDLE' as const,
  bestLapN: 3,
  pauseSeconds: 180,
  startedAt: null,
  stoppedAt: null,
  raceRunning: false,
};

const runningSession = {
  ...idleSession,
  status: 'RUNNING' as const,
  startedAt: '2024-01-01T10:00:00Z',
};

const timingRow = {
  position: 1,
  transponderNumber: 'T001',
  userId: 1,
  racerName: 'John Smith',
  laps: 5,
  bestLapMs: 35000,
  bestConsecutiveNMs: 108000,
  lastLapMs: 36000,
  runs: 1,
  isUnknown: false,
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/race-control/practice/42']}>
        <Routes>
          <Route path="/race-control/practice/:sessionId" element={children} />
          <Route path="/race-control/practice/:sessionId/print" element={<div>Print page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PracticeSessionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUsePracticeTiming.mockReturnValue({
      rows: [],
      unknownTransponders: [],
      isLoading: false,
    });
  });

  it('shows IDLE empty state before session starts', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(idleSession as never);

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText('Session not started')).toBeInTheDocument(),
    );
    expect(screen.getByText(/press start to begin timing/i)).toBeInTheDocument();
  });

  it('disables Start with a hint while a race is running', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue({ ...idleSession, raceRunning: true } as never);

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() => expect(screen.getByText('Session not started')).toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent(/a race is running/i);
    for (const button of screen.getAllByRole('button', { name: /start practice/i })) {
      expect(button).toBeDisabled();
    }
  });

  it('offers to try again when the session cannot be loaded, rather than saying it does not exist', async () => {
    vi.mocked(practiceApi.getSession).mockRejectedValueOnce(new Error('Network Error'));
    vi.mocked(practiceApi.getSession).mockResolvedValueOnce(idleSession as never);

    render(<PracticeSessionPage />, { wrapper });

    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(screen.queryByText('Session not found.')).not.toBeInTheDocument();
    expect(await screen.findByText('Session not started')).toBeInTheDocument();
  });

  it('says so when the session does not exist', async () => {
    vi.mocked(practiceApi.getSession).mockRejectedValue({ isAxiosError: true, response: { status: 404 } });

    render(<PracticeSessionPage />, { wrapper });

    expect(await screen.findByText('Session not found.')).toBeInTheDocument();
  });

  it('STOMP subscription updates live table on running session', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(runningSession as never);
    mockUsePracticeTiming.mockReturnValue({
      rows: [timingRow],
      unknownTransponders: [],
      isLoading: false,
    });

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText('John Smith')).toBeInTheDocument(),
    );
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('unknown transponder banner appears when unknown detected', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(runningSession as never);
    mockUsePracticeTiming.mockReturnValue({
      rows: [],
      unknownTransponders: ['T999'],
      isLoading: false,
    });

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText(/1 unknown transponder/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/T999/)).toBeInTheDocument();
  });

  it('best N consecutive column displays formatted lap time', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(runningSession as never);
    mockUsePracticeTiming.mockReturnValue({
      rows: [timingRow],
      unknownTransponders: [],
      isLoading: false,
    });

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByText('Best 3 Laps')).toBeInTheDocument(),
    );
    // bestConsecutiveNMs=108000, bestLapN=3 → avg = 36000ms → 36.000
    expect(screen.getByText('36.000 avg')).toBeInTheDocument();
  });

  it('stop button triggers stopSession mutation', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(runningSession as never);
    vi.mocked(practiceApi.stopSession).mockResolvedValue({ ...runningSession, status: 'STOPPED' } as never);

    render(<PracticeSessionPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /stop practice/i })).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByRole('button', { name: /stop practice/i }));

    await waitFor(() =>
      expect(vi.mocked(practiceApi.stopSession)).toHaveBeenCalledWith(42),
    );
  });

  it('tells the official why practice could not start, using the server\'s reason', async () => {
    vi.mocked(practiceApi.getSession).mockResolvedValue(idleSession as never);
    vi.mocked(practiceApi.startSession).mockRejectedValue(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
        status: 409,
        statusText: 'Conflict',
        headers: {},
        config: {} as never,
        data: { detail: "A race is running, so practice can't start until it has finished or been stopped" },
      }),
    );

    render(<PracticeSessionPage />, { wrapper });
    fireEvent.click((await screen.findAllByRole('button', { name: /start practice/i }))[0]);

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "A race is running, so practice can't start until it has finished or been stopped",
      ),
    );
  });

  it('keeps checking the session, since a race starting stops practice from the server', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      vi.mocked(practiceApi.getSession).mockResolvedValue(runningSession as never);
      render(<PracticeSessionPage />, { wrapper });
      await waitFor(() => expect(practiceApi.getSession).toHaveBeenCalledTimes(1));

      vi.mocked(practiceApi.getSession).mockResolvedValue({ ...runningSession, status: 'STOPPED' } as never);
      await vi.advanceTimersByTimeAsync(5100);

      await waitFor(() => expect(practiceApi.getSession).toHaveBeenCalledTimes(2));
      expect(await screen.findByText('STOPPED')).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
