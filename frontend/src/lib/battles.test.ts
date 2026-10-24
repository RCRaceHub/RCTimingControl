import { describe, expect, it } from 'vitest';
import { advanceBattles, groupBattles, type BattleHistory } from './battles';
import type { LiveTimingRowDto } from './raceControlApi';

function row(entryId: number, position: number, lapsCompleted: number, gapToAheadMs: number | null, extra: Partial<LiveTimingRowDto> = {}): LiveTimingRowDto {
  return { entryId, position, lapsCompleted, gapToAheadMs, intervalLapsDown: 0, finished: false, driverName: `D${entryId}`, ...extra } as LiveTimingRowDto;
}

/** Feeds one update per lap, each a list of rows, and returns the last result. */
function run(updates: LiveTimingRowDto[][], thresholdMs = 2000) {
  let history: BattleHistory = new Map();
  let result = advanceBattles(history, [], thresholdMs);
  for (const rows of updates) {
    result = advanceBattles(history, rows, thresholdMs);
    history = result.history;
  }
  return result;
}

describe('advanceBattles', () => {
  it('finds a pair within the threshold and ignores the leader and wide gaps', () => {
    const { battles } = run([[row(1, 1, 3, null), row(2, 2, 3, 1500), row(3, 3, 3, 4000)]]);
    expect(battles).toHaveLength(1);
    expect(battles[0].chaser.entryId).toBe(2);
    expect(battles[0].trend).toBe('unknown');
  });

  it('reports closing, and gaining fast, from the average change per lap', () => {
    const laps = [1800, 1500, 1200, 900].map((gap, i) => [row(1, 1, 3 + i, null), row(2, 2, 3 + i, gap)]);
    const [battle] = run(laps).battles;
    expect(battle.ratePerLapMs).toBeCloseTo(-300);
    expect(battle.trend).toBe('closing');
    expect(battle.gainingFast).toBe(true);
  });

  it('reports growing and holding', () => {
    const growing = [900, 1100, 1400].map((gap, i) => [row(1, 1, 3 + i, null), row(2, 2, 3 + i, gap)]);
    expect(run(growing).battles[0].trend).toBe('growing');
    const holding = [1000, 1050, 1000].map((gap, i) => [row(1, 1, 3 + i, null), row(2, 2, 3 + i, gap)]);
    expect(run(holding).battles[0].trend).toBe('holding');
  });

  it('keeps a pair for two laps after it drops out, then lets it go', () => {
    const lap = (i: number, gap: number) => [row(1, 1, 3 + i, null), row(2, 2, 3 + i, gap)];
    expect(run([lap(0, 1500), lap(1, 2500), lap(2, 3000)]).battles).toHaveLength(1);
    expect(run([lap(0, 1500), lap(1, 2500), lap(2, 3000), lap(3, 3500)]).battles).toHaveLength(0);
  });

  it('ignores a lapped car, a finished car and a zero threshold', () => {
    expect(run([[row(1, 1, 3, null), row(2, 2, 2, 500, { intervalLapsDown: 1 })]]).battles).toHaveLength(0);
    expect(run([[row(1, 1, 3, null), row(2, 2, 3, 500, { finished: true })]]).battles).toHaveLength(0);
    expect(run([[row(1, 1, 3, null), row(2, 2, 3, 500)]], 0).battles).toHaveLength(0);
  });
});

describe('groupBattles', () => {
  it('joins chained pairs into a train and keeps separate fights apart', () => {
    const { battles } = run([
      [row(1, 1, 3, null), row(2, 2, 3, 800), row(3, 3, 3, 1200), row(4, 4, 3, 6000), row(5, 5, 3, 900)],
    ]);
    const groups = groupBattles(battles);
    expect(groups.map((g) => g.cars.map((c) => c.entryId))).toEqual([[1, 2, 3], [4, 5]]);
    expect(groups[0].links).toHaveLength(2);
  });
});
