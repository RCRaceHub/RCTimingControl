import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** Cars this close, in milliseconds, with the one ahead are in a battle unless the page says otherwise (#505). */
export const DEFAULT_BATTLE_MS = 2000;
/** A gap that changes by more than this each lap, in milliseconds, is closing or growing rather than holding. */
export const TREND_MS_PER_LAP = 150;
/** A gap that closes by this much or more each lap, in milliseconds, marks a car that is gaining fast. */
export const GAINING_FAST_MS_PER_LAP = 300;
/** How many of the chaser's laps are judged for the trend: this many gaps, so one less change. */
const TREND_SAMPLES = 4;
/** Laps the chaser may run outside the threshold before the pair stops being a battle. */
const HOLD_LAPS = 2;

export type BattleTrend = 'closing' | 'growing' | 'holding' | 'unknown';

/** Two cars running close together, the chaser in the position directly behind the car ahead. */
export interface Battle {
  ahead: LiveTimingRowDto;
  chaser: LiveTimingRowDto;
  /** The chaser's gap to the car ahead as of its last crossing, in milliseconds. */
  gapMs: number;
  /** How much the gap changes each of the chaser's laps, in milliseconds: negative is closing. Null before two laps. */
  ratePerLapMs: number | null;
  trend: BattleTrend;
  /** Closing by {@link GAINING_FAST_MS_PER_LAP} or more a lap. */
  gainingFast: boolean;
}

interface Sample {
  chaserLaps: number;
  gapMs: number;
}

interface PairHistory {
  samples: Sample[];
  /** The chaser's lap count when the pair was last inside the threshold. */
  lastCloseLaps: number;
}

/** What the battle detector remembers between timing updates, keyed by `aheadEntryId:chaserEntryId`. */
export type BattleHistory = Map<string, PairHistory>;

/**
 * Takes one timing update into the history and works out the battles from it. Pure: the caller keeps the history.
 *
 * <p>A battle is a pair of adjacent cars on the same lap, within {@code thresholdMs}. The gap changes only as cars
 * cross the line, so it is sampled once per lap of the chaser, and the trend is the average change per lap over its
 * last few laps. A pair stays a battle for a couple of laps after it drops outside the threshold, so it does not
 * flicker on and off. A threshold of 0 or less switches battles off.
 *
 * @param history what the last update left, or an empty map at the start of a race
 * @param rows every row of the race, in running order
 * @param thresholdMs the largest gap that starts a battle, in milliseconds
 * @return the history to pass in next time, and the battles, the quickest closing first and then the nearest
 */
export function advanceBattles(
  history: BattleHistory,
  rows: LiveTimingRowDto[],
  thresholdMs: number,
): { history: BattleHistory; battles: Battle[] } {
  const next: BattleHistory = new Map();
  const battles: Battle[] = [];
  if (thresholdMs <= 0) return { history: next, battles };

  const byPosition = new Map(rows.map((r) => [r.position, r]));
  for (const chaser of rows) {
    const ahead = byPosition.get(chaser.position - 1);
    if (!ahead || chaser.finished || ahead.finished || chaser.intervalLapsDown > 0 || chaser.gapToAheadMs === null) {
      continue;
    }
    const key = `${ahead.entryId}:${chaser.entryId}`;
    const previous = history.get(key);
    const samples = previous ? [...previous.samples] : [];
    if (samples.length === 0 || samples[samples.length - 1].chaserLaps !== chaser.lapsCompleted) {
      samples.push({ chaserLaps: chaser.lapsCompleted, gapMs: chaser.gapToAheadMs });
    }
    const inRange = chaser.gapToAheadMs <= thresholdMs;
    const lastCloseLaps = inRange ? chaser.lapsCompleted : (previous?.lastCloseLaps ?? null);
    // A pair never inside the threshold is not worth remembering
    if (lastCloseLaps === null) continue;
    const trimmed = samples.slice(-TREND_SAMPLES);
    next.set(key, { samples: trimmed, lastCloseLaps });
    if (chaser.lapsCompleted - lastCloseLaps > HOLD_LAPS) continue;

    const first = trimmed[0];
    const last = trimmed[trimmed.length - 1];
    const ratePerLapMs = last.chaserLaps > first.chaserLaps ? (last.gapMs - first.gapMs) / (last.chaserLaps - first.chaserLaps) : null;
    const trend: BattleTrend =
      ratePerLapMs === null
        ? 'unknown'
        : ratePerLapMs <= -TREND_MS_PER_LAP
          ? 'closing'
          : ratePerLapMs >= TREND_MS_PER_LAP
            ? 'growing'
            : 'holding';
    battles.push({
      ahead,
      chaser,
      gapMs: chaser.gapToAheadMs,
      ratePerLapMs,
      trend,
      gainingFast: ratePerLapMs !== null && ratePerLapMs <= -GAINING_FAST_MS_PER_LAP,
    });
  }

  battles.sort((a, b) => Number(b.gainingFast) - Number(a.gainingFast) || a.gapMs - b.gapMs);
  return { history: next, battles };
}

/** A run of cars each in a battle with the next: a pair, or a train of three or more. */
export interface BattleGroup {
  /** The cars from the front of the group to the back. */
  cars: LiveTimingRowDto[];
  /** The battle between each car and the one behind it, front to back: one fewer than the cars. */
  links: Battle[];
  /** Any link is gaining fast. */
  gainingFast: boolean;
}

/**
 * Joins battles that share a car into one group, so a train of close cars reads as one story instead of several
 * overlapping pairs. Groups are ordered gaining-fast first, then by the tightest gap in the group.
 *
 * @param battles the battles from {@link advanceBattles}, in any order
 */
export function groupBattles(battles: Battle[]): BattleGroup[] {
  const groups: BattleGroup[] = [];
  let current: BattleGroup | null = null;
  for (const battle of [...battles].sort((a, b) => a.ahead.position - b.ahead.position)) {
    const tail: LiveTimingRowDto | undefined = current?.cars[current.cars.length - 1];
    if (current && tail?.entryId === battle.ahead.entryId) {
      current.cars.push(battle.chaser);
      current.links.push(battle);
      current.gainingFast ||= battle.gainingFast;
    } else {
      current = { cars: [battle.ahead, battle.chaser], links: [battle], gainingFast: battle.gainingFast };
      groups.push(current);
    }
  }
  const tightest = (g: BattleGroup) => Math.min(...g.links.map((l) => l.gapMs));
  return groups.sort((a, b) => Number(b.gainingFast) - Number(a.gainingFast) || tightest(a) - tightest(b));
}
