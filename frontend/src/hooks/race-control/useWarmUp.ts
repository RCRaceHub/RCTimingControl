import { useStomp } from './useStomp';

type WarmUpFrame = { raceId: number; seenEntryIds: number[] };

/**
 * The entries of a race on the grid that the decoder has seen on the warm-up lap (#482). Each broadcast carries the
 * whole list, so the latest one replaces whatever came before; the list from the readiness fetch stands in until the
 * first broadcast, and after a reload.
 */
export function useWarmUp(raceId: number | null, snapshot: number[] | undefined): Set<number> {
  const { data: pushed } = useStomp<WarmUpFrame>(raceId ? `/topic/race/${raceId}/warmup` : null);
  const current = pushed && pushed.raceId === raceId ? pushed : null;
  return new Set(current?.seenEntryIds ?? snapshot ?? []);
}
