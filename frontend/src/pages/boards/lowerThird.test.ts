import { describe, expect, it } from 'vitest';
import type { BattleGroup } from '@/lib/battles';
import { battleCandidate } from './lowerThird';

const car = (entryId: number, position: number) => ({ entryId, position, driverName: `D${entryId}`, carNumber: String(entryId) });
const group = (...cars: ReturnType<typeof car>[]): BattleGroup => ({ cars, links: [], gainingFast: false });

describe('battleCandidate', () => {
  it('features the leader of a battle in the top places', () => {
    expect(battleCandidate([group(car(5, 2), car(6, 3))])).toBe(5);
  });

  it('ignores battles further down the order', () => {
    expect(battleCandidate([group(car(8, 6), car(9, 7))])).toBeNull();
  });

  it('takes the first battle that reaches the top places', () => {
    expect(battleCandidate([group(car(8, 6), car(9, 7)), group(car(1, 1), car(2, 2))])).toBe(1);
  });

  it('is null with no battles', () => {
    expect(battleCandidate([])).toBeNull();
  });
});
