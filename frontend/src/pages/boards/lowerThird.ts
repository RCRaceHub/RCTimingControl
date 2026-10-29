import type { BattleGroup } from '@/lib/battles';

/** Battles are only worth a lower third when they involve a car in these top places. */
export const LOWER_THIRD_TOP_PLACES = 3;

/** How long a lower third stays up before it hides itself, and how long before a battle may bring the same car up again. */
export const LOWER_THIRD_HOLD_MS = 8000;
export const LOWER_THIRD_COOLDOWN_MS = 90_000;

/**
 * The car a battle should feature (#533): the leader of the first battle that has a car in the top places, as the
 * group's cars run front to back. Null when no battle reaches that high.
 */
export function battleCandidate(groups: BattleGroup[], topPlaces: number = LOWER_THIRD_TOP_PLACES): number | null {
  for (const group of groups) {
    if (group.cars.some((car) => car.position <= topPlaces)) return group.cars[0].entryId;
  }
  return null;
}
