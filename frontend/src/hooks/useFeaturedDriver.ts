import { useEffect, useState } from 'react';
import { useStomp } from '@/hooks/race-control/useStomp';
import type { BattleGroup } from '@/lib/battles';
import {
  LOWER_THIRD_COOLDOWN_MS,
  LOWER_THIRD_HOLD_MS,
  battleCandidate,
} from '@/pages/boards/lowerThird';
import type { LowerThirdMode } from '@/pages/boards/boardFormat';

type FeaturedFrame = { raceId: number; entryId: number };

/**
 * The car the overlay's lower third is showing right now (#533), or null. A car comes up when the race director sends
 * one (the race's featured topic, which anonymous screens may follow), or, once a lap has been counted, when a battle
 * reaches the top places; each is switched by {@code mode}. It hides itself after a few seconds, a director's pick
 * replaces a battle's, and a battle does not bring the same car up again for a minute and a half.
 */
export function useFeaturedDriver(
  raceId: number | null,
  mode: LowerThirdMode,
  battles: BattleGroup[],
  battlesLive: boolean,
): number | null {
  const [shown, setShown] = useState<{ raceId: number; entryId: number } | null>(null);
  // Cars a battle brought up lately, which it may not bring up again until the cooldown ends
  const [cooling, setCooling] = useState<ReadonlySet<number>>(new Set());
  const directorOn = mode === 'director' || mode === 'both';
  const battleOn = mode === 'battle' || mode === 'both';

  // State is adjusted while rendering, not in an effect, when a new frame or a new battle candidate arrives
  const { data: frame } = useStomp<FeaturedFrame>(raceId && directorOn ? `/topic/race/${raceId}/featured` : null);
  const [seenFrame, setSeenFrame] = useState<FeaturedFrame | null>(null);
  if (frame !== seenFrame) {
    setSeenFrame(frame);
    if (frame) setShown({ raceId: frame.raceId, entryId: frame.entryId });
  }

  const candidate = battleOn && battlesLive ? battleCandidate(battles) : null;
  const [seenCandidate, setSeenCandidate] = useState<number | null>(null);
  if (candidate !== seenCandidate) {
    setSeenCandidate(candidate);
    // A director's pick that is still up is not pushed off by a battle
    if (candidate !== null && raceId !== null && !cooling.has(candidate) && shown === null) {
      setShown({ raceId, entryId: candidate });
    }
  }

  useEffect(() => {
    if (!shown) return;
    const timer = setTimeout(() => {
      setShown(null);
      setCooling((current) => new Set(current).add(shown.entryId));
      setTimeout(() => setCooling((current) => {
        const next = new Set(current);
        next.delete(shown.entryId);
        return next;
      }), LOWER_THIRD_COOLDOWN_MS);
    }, LOWER_THIRD_HOLD_MS);
    return () => clearTimeout(timer);
  }, [shown]);

  // A car from another race is never shown
  return shown && shown.raceId === raceId ? shown.entryId : null;
}
