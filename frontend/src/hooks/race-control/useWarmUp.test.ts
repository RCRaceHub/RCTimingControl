import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';

const stomp = vi.hoisted(() => ({ data: null as { raceId: number; seenEntryIds: number[] } | null }));
vi.mock('./useStomp', () => ({ useStomp: () => ({ data: stomp.data, status: 'connected' }) }));

import { useWarmUp } from './useWarmUp';

describe('useWarmUp', () => {
  it('starts from the list fetched with the readiness', () => {
    stomp.data = null;

    const { result } = renderHook(() => useWarmUp(7, [3, 4]));

    expect([...result.current]).toEqual([3, 4]);
  });

  it('takes the latest broadcast over the fetched list', () => {
    stomp.data = { raceId: 7, seenEntryIds: [3, 4, 5] };

    const { result } = renderHook(() => useWarmUp(7, [3]));

    expect([...result.current]).toEqual([3, 4, 5]);
  });
});
