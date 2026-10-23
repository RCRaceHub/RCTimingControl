import { describe, it, expect, vi, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RaceClockDisplay } from './RaceClockDisplay';
import type { RaceClockDto } from '@/lib/boardsApi';
import { getForcedClose } from '@/lib/boardsApi';

vi.mock('@/lib/boardsApi', async (orig) => ({
  ...(await orig<typeof import('@/lib/boardsApi')>()),
  getForcedClose: vi.fn(),
}));

const clock = (over: Partial<RaceClockDto>): RaceClockDto => ({
  raceId: 1, status: 'RUNNING', elapsedMs: 0, durationMs: 300_000, remainingMs: 300_000, running: true, ...over,
});

describe('RaceClockDisplay', () => {
  afterEach(() => vi.useRealTimers());

  it('counts the time left down while the race runs', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-15T12:00:00Z'));
    render(<RaceClockDisplay clock={clock({ remainingMs: 125_000 })} readAt={Date.now()} />);
    expect(screen.getByRole('timer')).toHaveTextContent('Time left2:05');

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(screen.getByRole('timer')).toHaveTextContent('2:00');
  });

  it('says a rolling start is waiting for its first car, not stopped', () => {
    render(<RaceClockDisplay clock={clock({ running: false, status: 'RUNNING' })} readAt={0} />);

    expect(screen.getByText('waiting for first car')).toBeInTheDocument();
    expect(screen.queryByText('stopped')).not.toBeInTheDocument();
  });

  it('holds still and says so while the race is stopped', () => {
    render(<RaceClockDisplay clock={clock({ running: false, status: 'STOPPED', remainingMs: 90_000 })} readAt={0} />);
    expect(screen.getByRole('timer')).toHaveTextContent('1:30');
    expect(screen.getByText('stopped')).toBeInTheDocument();
  });

  it('shows the time so far for a race with no set length', () => {
    const untimed = clock({ durationMs: null, remainingMs: null, elapsedMs: 61_000 });
    render(<RaceClockDisplay clock={untimed} readAt={Date.now()} />);
    expect(screen.getByRole('timer')).toHaveTextContent('Race time1:01');
  });

  it('shows nothing until the clock is read', () => {
    render(<RaceClockDisplay clock={undefined} readAt={0} />);
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
  });

  it('counts down to the forced close once the race time is up', async () => {
    vi.mocked(getForcedClose).mockResolvedValue({ closesInMs: 18_000 });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RaceClockDisplay clock={clock({ remainingMs: 0, elapsedMs: 305_000 })} readAt={Date.now()} />
      </QueryClientProvider>,
    );
    expect(await screen.findByRole('timer', { name: 'Time until the race is closed' })).toHaveTextContent(
      'Closes in0:1',
    );
  });

  it('shows no forced close countdown while there is race time left', () => {
    render(<RaceClockDisplay clock={clock({ remainingMs: 60_000 })} readAt={Date.now()} />);
    expect(screen.queryByText('Closes in')).not.toBeInTheDocument();
  });
});
