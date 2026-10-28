import { useEffect, useRef, useState } from 'react';
import { ClockOffsetEstimator } from '@/lib/clockOffset';
import type { LapBarClock } from '@/components/LapProgressBar';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * What the lap bars need to read the clock (#479): the offset between this device and the timing laptop,
 * learned from the passings as they arrive, and when the race was last resumed from a stop. Without the second,
 * every bar would turn red the moment a stopped race resumes, since the stoppage is not taken out of the lap.
 *
 * <p>The returned clock is one object that is updated in place, because the bars read it on their own ticks and
 * nothing else on the page needs to re-render when it changes.
 *
 * @param rows the live timing rows
 * @param status the race's status
 * @param lateMarginMs how far past its average a lap runs before its bar turns red: the club's setting
 */
export function useLapBarClock(
  rows: LiveTimingRowDto[],
  status: string | undefined,
  lateMarginMs?: number,
): LapBarClock {
  const [initial] = useState(() => ({
    estimator: new ClockOffsetEstimator(),
    clock: { offsetMs: 0, resumedAtMs: null } as LapBarClock,
    status,
    resumedAtLocal: null as number | null,
  }));
  // The same object, held in a ref so that the effects below may update it in place
  const held = useRef(initial);

  // The rows are a new array on every update, so this runs once per update, not once per tick
  useEffect(() => {
    const state = held.current;
    state.clock.offsetMs = state.estimator.observe(rows, Date.now());
    state.clock.offsetKnown = state.estimator.known;
    if (state.resumedAtLocal !== null) state.clock.resumedAtMs = state.resumedAtLocal + state.clock.offsetMs;
  }, [rows]);

  useEffect(() => {
    const state = held.current;
    if (state.status === status) return;
    const resumed = state.status === 'STOPPED' && status === 'RUNNING';
    if (resumed) state.resumedAtLocal = Date.now();
    else if (status !== 'RUNNING') state.resumedAtLocal = null;
    state.clock.resumedAtMs = state.resumedAtLocal === null ? null : state.resumedAtLocal + state.clock.offsetMs;
    state.status = status;
  }, [status]);

  useEffect(() => {
    held.current.clock.lateMarginMs = lateMarginMs;
  }, [lateMarginMs]);

  return initial.clock;
}
