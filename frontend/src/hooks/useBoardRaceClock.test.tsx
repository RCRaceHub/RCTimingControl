import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useBoardRaceClock } from './useBoardRaceClock';
import type { RaceClockDto } from '@/lib/boardsApi';

vi.mock('@/lib/boardsApi', () => ({ getForcedClose: vi.fn() }));
import { getForcedClose } from '@/lib/boardsApi';

function clock(overrides: Partial<RaceClockDto> = {}): RaceClockDto {
  return { raceId: 12, status: 'RUNNING', elapsedMs: 75_000, durationMs: 300_000, remainingMs: 225_000, running: false, ...overrides };
}

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('useBoardRaceClock', () => {
  beforeEach(() => vi.clearAllMocks());

  it('has no text before a clock has arrived', () => {
    const { result } = renderHook(() => useBoardRaceClock(12, undefined, 0), { wrapper });
    expect(result.current).toEqual({ text: null, overrun: false });
  });

  it('shows the time left in a timed race and the time so far in one with no length', () => {
    const timed = renderHook(() => useBoardRaceClock(12, clock(), Date.now()), { wrapper });
    expect(timed.result.current).toEqual({ text: '3:45', overrun: false });

    const open = renderHook(() => useBoardRaceClock(12, clock({ durationMs: null, remainingMs: null }), Date.now()), { wrapper });
    expect(open.result.current).toEqual({ text: '1:15', overrun: false });
    expect(getForcedClose).not.toHaveBeenCalled();
  });

  it('counts down the time left to finish once a running race is past its time', async () => {
    vi.mocked(getForcedClose).mockResolvedValue({ closesInMs: 42_000 });
    const { result } = renderHook(
      () => useBoardRaceClock(12, clock({ elapsedMs: 301_000, remainingMs: 0, running: true }), Date.now()),
      { wrapper },
    );

    expect(result.current.overrun).toBe(true);
    await waitFor(() => expect(result.current.text).toBe('0:42'));
  });

  it('reads 0:00 while overrun with no limit counting down', async () => {
    vi.mocked(getForcedClose).mockResolvedValue({ closesInMs: null });
    const { result } = renderHook(
      () => useBoardRaceClock(12, clock({ elapsedMs: 301_000, remainingMs: 0, running: true }), Date.now()),
      { wrapper },
    );

    await waitFor(() => expect(getForcedClose).toHaveBeenCalled());
    expect(result.current).toEqual({ text: '0:00', overrun: true });
  });

  it('stays neutral past the time when the race is stopped', () => {
    const { result } = renderHook(
      () => useBoardRaceClock(12, clock({ elapsedMs: 301_000, remainingMs: 0, running: false }), Date.now()),
      { wrapper },
    );
    expect(result.current).toEqual({ text: '0:00', overrun: false });
    expect(getForcedClose).not.toHaveBeenCalled();
  });
});
