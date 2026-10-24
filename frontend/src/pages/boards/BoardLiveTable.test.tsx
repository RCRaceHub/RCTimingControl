import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BoardLiveTable } from './BoardLiveTable';
import type { LapBarClock } from '@/components/LapProgressBar';
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
    expect(screen.getByRole('columnheader', { name: 'Ahead' })).toBeInTheDocument();
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
    // The bars read this device's clock plus the offset, so an offset puts "now" where the test wants it
    const at = (nowMs: number): LapBarClock => ({ offsetMs: nowMs - Date.now(), resumedAtMs: null });
    const rows = [row(1)];
    const { rerender } = render(<BoardLiveTable rows={rows} barClock={at(104_000)} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'under');
    rerender(<BoardLiveTable rows={rows} barClock={at(110_500)} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'at');
    rerender(<BoardLiveTable rows={rows} barClock={at(112_000)} />);
    expect(screen.getByTestId('lap-progress')).toHaveAttribute('data-state', 'late');
  });

  it('leaves the bars out without a clock', () => {
    render(<BoardLiveTable rows={[row(1)]} />);
    expect(screen.queryByTestId('lap-progress')).toBeNull();
  });

  it('shows places gained or lost against the grid only when asked to', () => {
    const rows = [row(1, { startPosition: 3 }), row(2, { startPosition: 1 }), row(3, { startPosition: 3 })];
    const { container, rerender } = render(<BoardLiveTable rows={rows} showArrows />);
    expect(container.querySelectorAll('[data-direction="up"]')).toHaveLength(1);
    expect(container.querySelectorAll('[data-direction="down"]')).toHaveLength(1);
    expect(screen.getByLabelText('Up 2')).toBeInTheDocument();
    expect(screen.getByLabelText('Down 1')).toBeInTheDocument();
    rerender(<BoardLiveTable rows={rows} />);
    expect(container.querySelectorAll('[data-direction]')).toHaveLength(0);
  });

  it('adds a predicted laps column given a bar clock and the time left', () => {
    const clock: LapBarClock = { offsetMs: 100_000 - Date.now(), resumedAtMs: null };
    render(<BoardLiveTable rows={[row(1)]} barClock={clock} remainingAt={() => 25_000} />);
    expect(screen.getByRole('columnheader', { name: 'Pred' })).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });
});
