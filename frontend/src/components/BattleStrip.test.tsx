import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BattleStrip } from './BattleStrip';
import { gainingFastEntryIds, type BattleGroup, type BattleLink } from '@/lib/battles';

const car = (entryId: number) => ({ entryId, position: entryId, driverName: `Driver ${entryId}`, carNumber: null });
const link = (trend: BattleLink['trend'], rate: number | null, gainingFast = false): BattleLink => ({
  gapMs: 800,
  ratePerLapMs: rate,
  trend,
  gainingFast,
});

function pair(a: number, b: number, trend: BattleLink['trend'], rate: number | null, gainingFast = false): BattleGroup {
  return { cars: [car(a), car(b)], links: [link(trend, rate, gainingFast)], gainingFast };
}

describe('BattleStrip', () => {
  it('renders nothing with no battles', () => {
    const { container } = render(<BattleStrip groups={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('says whether the gap is closing or growing', () => {
    render(<BattleStrip groups={[pair(1, 2, 'CLOSING', -400, true), pair(3, 4, 'GROWING', 300)]} />);
    expect(screen.getByText(/Driver 2 is 0.8s behind Driver 1, closing by 0.4s a lap/).parentElement).toHaveAttribute(
      'data-gaining-fast',
      'true',
    );
    expect(screen.getByText(/Driver 4 is 0.8s behind Driver 3, pulling away by 0.3s a lap/).parentElement).toHaveAttribute(
      'data-trend',
      'GROWING',
    );
  });

  it('labels a pair by the place it is fighting for', () => {
    render(<BattleStrip groups={[pair(4, 5, 'CLOSING', -200)]} />);
    expect(screen.getByTestId('battle-label')).toHaveTextContent('Battle for P4');
  });

  it('shows a train as one line', () => {
    const train: BattleGroup = {
      cars: [car(1), car(2), car(3)],
      links: [link('HOLDING', 0), link('CLOSING', -200)],
      gainingFast: false,
    };
    render(<BattleStrip groups={[train]} />);
    expect(screen.getAllByTestId('battle')).toHaveLength(1);
    expect(screen.getByTestId('battle')).toHaveAttribute('data-train', 'true');
    expect(screen.getByTestId('battle-label')).toHaveTextContent('Train P1–P3');
  });

  it('shows two at a time when there are more', () => {
    render(
      <BattleStrip groups={[pair(1, 2, 'CLOSING', -200), pair(3, 4, 'CLOSING', -200), pair(5, 6, 'CLOSING', -200)]} />,
    );
    expect(screen.getAllByTestId('battle')).toHaveLength(2);
  });
});

describe('gainingFastEntryIds', () => {
  it('names the car behind each link that is gaining fast', () => {
    const train: BattleGroup = {
      cars: [car(1), car(2), car(3)],
      links: [link('HOLDING', 0), link('CLOSING', -400, true)],
      gainingFast: true,
    };
    expect(gainingFastEntryIds([train, pair(7, 8, 'GROWING', 300)])).toEqual(new Set([3]));
  });
});
