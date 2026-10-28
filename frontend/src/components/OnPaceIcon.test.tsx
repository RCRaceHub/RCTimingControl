import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { OnPaceIcon } from './OnPaceIcon';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const row = (over: Partial<LiveTimingRowDto>): LiveTimingRowDto => ({
  entryId: 1, driverName: 'A', carNumber: '1', position: 2, lapsCompleted: 5, lastPassingTimeMs: Date.now(),
  lastLapMs: 10_000, bestLapMs: 9_500, avgLapMs: 10_000, overallFastestLapMs: 9_000, lapsDown: 0,
  intervalLapsDown: 0, gapToLeaderMs: 3_000, gapToAheadMs: 800, totalTimeMs: 50_000, ...over,
});

describe('OnPaceIcon', () => {
  it('shows a thumbs up for a car running to its average', () => {
    render(<OnPaceIcon row={row({})} clock={{ offsetMs: 0, resumedAtMs: null }} />);
    expect(screen.getByRole('img', { name: 'On pace for its predicted laps' })).toBeInTheDocument();
  });

  it('shows nothing for a car slower than its average', () => {
    render(<OnPaceIcon row={row({ lastLapMs: 12_000 })} clock={{ offsetMs: 0, resumedAtMs: null }} />);
    expect(screen.queryByRole('img')).toBeNull();
  });
});
