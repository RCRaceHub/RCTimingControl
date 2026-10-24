import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BoardLiveTable } from './BoardLiveTable';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const row = (position: number, over: Partial<LiveTimingRowDto> = {}): LiveTimingRowDto => ({
  entryId: position, driverName: `Driver ${position}`, carNumber: String(position), position, lapsCompleted: 4,
  lastPassingTimeMs: 100_000, lastLapMs: 10_000, bestLapMs: 9_800, avgLapMs: 10_000, overallFastestLapMs: 9_500,
  lapsDown: 0, intervalLapsDown: 0, gapToLeaderMs: position === 1 ? null : 3_000, gapToAheadMs: position === 1 ? null : 2_500,
  totalTimeMs: 40_000, ...over,
});

describe('BoardLiveTable', () => {
  it('shows the gap to the car ahead as well as to the leader', () => {
    render(<BoardLiveTable rows={[row(1), row(2, { gapToAheadMs: 2_500, gapToLeaderMs: 6_000 })]} />);
    expect(screen.getByRole('columnheader', { name: 'Int' })).toBeInTheDocument();
    expect(screen.getByText('+6.000')).toBeInTheDocument();
    expect(screen.getByText('+2.500')).toBeInTheDocument();
  });

  it('marks a car close to the one ahead only when asked to', () => {
    const rows = [row(1), row(2, { gapToAheadMs: 600 }), row(3, { gapToAheadMs: 4_000 })];
    const { container, rerender } = render(<BoardLiveTable rows={rows} closeMs={1000} />);
    expect(container.querySelectorAll('tr[data-close]')).toHaveLength(1);
    rerender(<BoardLiveTable rows={rows} />);
    expect(container.querySelectorAll('tr[data-close]')).toHaveLength(0);
  });

  it('draws a lap bar that is green, yellow or red against the average lap', () => {
    const rows = [row(1)];
    const { rerender } = render(<BoardLiveTable rows={rows} nowMs={104_000} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'under');
    rerender(<BoardLiveTable rows={rows} nowMs={110_500} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'at');
    rerender(<BoardLiveTable rows={rows} nowMs={112_000} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'late');
  });

  it('leaves the bars out without a clock', () => {
    render(<BoardLiveTable rows={[row(1)]} />);
    expect(screen.queryByTestId('lap-progress')).toBeNull();
  });
});
