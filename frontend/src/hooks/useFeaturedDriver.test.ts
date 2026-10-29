import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import type { BattleGroup } from '@/lib/battles';
import { LOWER_THIRD_HOLD_MS } from '@/pages/boards/lowerThird';
import { useFeaturedDriver } from './useFeaturedDriver';

let frame: { raceId: number; entryId: number } | null = null;
vi.mock('@/hooks/race-control/useStomp', () => ({ useStomp: (topic: string | null) => ({ data: topic ? frame : null }) }));

const car = (entryId: number, position: number) => ({ entryId, position, driverName: 'D', carNumber: null });
const battle: BattleGroup[] = [{ cars: [car(8, 1), car(9, 2)], links: [], gainingFast: false }];

describe('useFeaturedDriver', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    frame = null;
  });
  afterEach(() => vi.useRealTimers());

  it('shows the car the race director sends, then hides it', () => {
    const { result, rerender } = renderHook(() => useFeaturedDriver(3, 'both', [], true));
    expect(result.current).toBeNull();

    frame = { raceId: 3, entryId: 5 };
    rerender();
    expect(result.current).toBe(5);

    act(() => void vi.advanceTimersByTime(LOWER_THIRD_HOLD_MS + 1));
    expect(result.current).toBeNull();
  });

  it('shows the leader of a battle for the top places when battles may trigger it', () => {
    const { result } = renderHook(() => useFeaturedDriver(3, 'battle', battle, true));
    expect(result.current).toBe(8);
  });

  it('ignores battles in director-only mode and the director in battle-only mode', () => {
    expect(renderHook(() => useFeaturedDriver(3, 'director', battle, true)).result.current).toBeNull();
    frame = { raceId: 3, entryId: 5 };
    expect(renderHook(() => useFeaturedDriver(3, 'battle', [], true)).result.current).toBeNull();
  });

  it('shows nothing when switched off', () => {
    frame = { raceId: 3, entryId: 5 };
    expect(renderHook(() => useFeaturedDriver(3, 'off', battle, true)).result.current).toBeNull();
  });
});
