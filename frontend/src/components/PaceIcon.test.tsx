import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PaceIcon } from './PaceIcon';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const row = (over: Partial<LiveTimingRowDto>): LiveTimingRowDto => ({
  entryId: 1, driverName: 'A', carNumber: '1', position: 2, lapsCompleted: 5, lastPassingTimeMs: Date.now(),
  lastLapMs: 10_000, bestLapMs: 9_500, avgLapMs: 10_000, overallFastestLapMs: 9_000, lapsDown: 0,
  intervalLapsDown: 0, gapToLeaderMs: 3_000, gapToAheadMs: 800, totalTimeMs: 50_000, ...over,
});

describe('PaceIcon', () => {
  it('shows a thumbs up for a car running to its average', () => {
    render(<PaceIcon row={row({})} clock={{ offsetMs: 0, resumedAtMs: null }} />);
    expect(screen.getByRole('img', { name: 'On pace for its predicted laps' })).toBeInTheDocument();
  });

  it('shows a thumbs down for a car slower than its average', () => {
    render(<PaceIcon row={row({ lastLapMs: 12_000 })} clock={{ offsetMs: 0, resumedAtMs: null }} />);
    expect(screen.getByRole('img', { name: 'Off pace for its predicted laps' })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'On pace for its predicted laps' })).toBeNull();
  });

  it('shows nothing for a car with no average lap or one that has finished', () => {
    const clock = { offsetMs: 0, resumedAtMs: null };
    const { rerender } = render(<PaceIcon row={row({ avgLapMs: null })} clock={clock} />);
    expect(screen.queryByRole('img')).toBeNull();
    rerender(<PaceIcon row={row({ finished: true })} clock={clock} />);
    expect(screen.queryByRole('img')).toBeNull();
  });
});
