import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { FlyingLapIcon, LappedPill, RowMarkers } from './RowBadges';
import { signalLost } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

function row(overrides: Partial<LiveTimingRowDto> = {}): LiveTimingRowDto {
  return {
    entryId: 1, driverName: 'Grace Hopper', carNumber: '1', position: 2, lapsCompleted: 5, lastPassingTimeMs: 100_000,
    lastLapMs: 15_000, bestLapMs: 15_000, avgLapMs: 15_000, overallFastestLapMs: 14_000, lapsDown: 0,
    intervalLapsDown: 0, gapToLeaderMs: 1000, gapToAheadMs: 1000, totalTimeMs: 75_000, ...overrides,
  };
}

describe('LappedPill', () => {
  it('shows how many laps down, in words for the screen reader', () => {
    render(<LappedPill row={row({ lapsDown: 2 })} />);
    expect(screen.getByRole('img', { name: '2 laps down' })).toHaveTextContent('+2');
  });

  it('says nothing for a car on the lead lap', () => {
    const { container } = render(<LappedPill row={row()} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('RowMarkers', () => {
  it('shows the penalty and no-signal icons only when they apply', () => {
    const { rerender } = render(<RowMarkers row={row()} noSignal={false} />);
    expect(screen.queryByRole('img')).toBeNull();
    rerender(<RowMarkers row={row({ timePenaltyMs: 5000 })} noSignal />);
    expect(screen.getByRole('img', { name: 'Has a time penalty' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Not seen by the decoder for a while' })).toBeInTheDocument();
  });
});

describe('signalLost', () => {
  it('needs three average laps (and 15 s) of silence from a car still racing', () => {
    expect(signalLost(row(), 100_000 + 44_000)).toBe(false);
    expect(signalLost(row(), 100_000 + 46_000)).toBe(true);
    expect(signalLost(row({ avgLapMs: 2000 }), 100_000 + 14_000)).toBe(false);
  });

  it('never fires for a finished car or one with no average yet, and counts from a resume', () => {
    expect(signalLost(row({ finished: true }), 500_000)).toBe(false);
    expect(signalLost(row({ avgLapMs: null }), 500_000)).toBe(false);
    expect(signalLost(row(), 500_000, 499_000)).toBe(false);
  });
});

describe('FlyingLapIcon', () => {
  it('shows a flame while the latest lap is a flying lap', () => {
    render(<FlyingLapIcon row={row({ flyingLap: true })} />);
    expect(screen.getByRole('img')).toHaveAttribute('data-state', 'flying');
  });

  it('shows nothing otherwise, or once the car has finished', () => {
    const { container, rerender } = render(<FlyingLapIcon row={row()} />);
    expect(container).toBeEmptyDOMElement();
    rerender(<FlyingLapIcon row={row({ flyingLap: true, finished: true })} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('comes with the other markers beside a name', () => {
    render(<RowMarkers row={row({ flyingLap: true })} noSignal={false} />);
    expect(screen.getByRole('img')).toHaveAttribute('data-state', 'flying');
  });
});
