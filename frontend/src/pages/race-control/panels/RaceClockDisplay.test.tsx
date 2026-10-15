import { describe, it, expect, vi, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { RaceClockDisplay } from './RaceClockDisplay';
import type { RaceClockDto } from '@/lib/boardsApi';

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

  it('holds still and says so while the race is stopped', () => {
    render(<RaceClockDisplay clock={clock({ running: false, status: 'STOPPED', remainingMs: 90_000 })} readAt={0} />);
    expect(screen.getByRole('timer')).toHaveTextContent('1:30');
    expect(screen.getByText('stopped')).toBeInTheDocument();
  });

  it('shows the time so far for a race with no set length', () => {
    render(
      <RaceClockDisplay clock={clock({ durationMs: null, remainingMs: null, elapsedMs: 61_000 })} readAt={Date.now()} />,
    );
    expect(screen.getByRole('timer')).toHaveTextContent('Race time1:01');
  });

  it('shows nothing until the clock is read', () => {
    render(<RaceClockDisplay clock={undefined} readAt={0} />);
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
  });
});
