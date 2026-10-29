import { describe, expect, it } from 'vitest';
import { battleChasers, type BattleCar, type BattleGroup, type BattleLink } from './battles';

const car = (entryId: number, position: number): BattleCar => ({ entryId, position, driverName: `Driver ${entryId}`, carNumber: null });
const link = (gapMs: number): BattleLink => ({ gapMs, ratePerLapMs: null, trend: 'UNKNOWN', gainingFast: false });

describe('battleChasers', () => {
  it('is empty with no battles', () => {
    expect(battleChasers([]).size).toBe(0);
  });

  it('gives a pair its chaser and the gap to the car ahead', () => {
    const groups: BattleGroup[] = [{ cars: [car(1, 1), car(2, 2)], links: [link(800)], gainingFast: false }];
    const chasers = battleChasers(groups);
    expect([...chasers.keys()]).toEqual([2]);
    expect(chasers.get(2)?.gapMs).toBe(800);
  });

  it('gives every car behind the leader of a train its own link', () => {
    const groups: BattleGroup[] = [{ cars: [car(1, 1), car(2, 2), car(3, 3)], links: [link(500), link(900)], gainingFast: false }];
    const chasers = battleChasers(groups);
    expect([...chasers.keys()]).toEqual([2, 3]);
    expect(chasers.get(3)?.gapMs).toBe(900);
  });
});
