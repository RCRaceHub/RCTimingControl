import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BattleStrip } from './BattleStrip';
import type { Battle, BattleGroup } from '@/lib/battles';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

const car = (entryId: number, position: number) => ({ entryId, position, driverName: `Driver ${entryId}` }) as LiveTimingRowDto;

function pair(a: number, b: number, trend: Battle['trend'], rate: number | null, gainingFast = false): BattleGroup {
  const link = { ahead: car(a, a), chaser: car(b, b), gapMs: 800, ratePerLapMs: rate, trend, gainingFast } as Battle;
  return { cars: [link.ahead, link.chaser], links: [link], gainingFast };
}

describe('BattleStrip', () => {
  it('renders nothing with no battles', () => {
    const { container } = render(<BattleStrip groups={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('says whether the gap is closing or growing', () => {
    render(<BattleStrip groups={[pair(1, 2, 'closing', -400, true), pair(3, 4, 'growing', 300)]} />);
    expect(screen.getByText(/Driver 2 is 0.8s behind Driver 1, closing by 0.4s a lap/).parentElement!).toHaveAttribute('data-gaining-fast', 'true');
    expect(screen.getByText(/Driver 4 is 0.8s behind Driver 3, pulling away by 0.3s a lap/).parentElement!).toHaveAttribute('data-trend', 'growing');
  });

  it('labels a pair by the place it is fighting for', () => {
    render(<BattleStrip groups={[pair(4, 5, 'closing', -200)]} />);
    expect(screen.getByTestId('battle-label')).toHaveTextContent('Battle for P4');
  });

  it('shows a train as one line', () => {
    const a = pair(1, 2, 'holding', 0).links[0];
    const b = pair(2, 3, 'closing', -200).links[0];
    render(<BattleStrip groups={[{ cars: [a.ahead, a.chaser, b.chaser], links: [a, b], gainingFast: false }]} />);
    expect(screen.getAllByTestId('battle')).toHaveLength(1);
    expect(screen.getByTestId('battle')).toHaveAttribute('data-train', 'true');
    expect(screen.getByTestId('battle-label')).toHaveTextContent('Train P1–P3');
  });

  it('shows two at a time when there are more', () => {
    render(<BattleStrip groups={[pair(1, 2, 'closing', -200), pair(3, 4, 'closing', -200), pair(5, 6, 'closing', -200)]} />);
    expect(screen.getAllByTestId('battle')).toHaveLength(2);
  });
});
