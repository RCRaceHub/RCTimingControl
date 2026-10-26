import { useRef, useEffect, useState } from 'react';
import { everyoneOffLapZero } from '@/lib/lapProgress';

interface HasLapInfo {
  entryId: number;
  lapsCompleted: number;
}

/**
 * Returns the set of entryIds that have been continuously lapped (lap count
 * below the leader) for at least `debounceMs` milliseconds.
 *
 * The debounce prevents the "LAPPED" badge from flashing during the brief
 * window between the leader crossing the line and the backmarker doing so
 * on the same lap (typically 1-5 seconds at club RC speeds). Nobody is lapped while any car is still on lap 0.
 */
export function useLappedBadge(rows: HasLapInfo[], debounceMs = 5000): Set<number> {
  const firstLappedAt = useRef<Map<number, number>>(new Map());
  const [lappedSet, setLappedSet] = useState<Set<number>>(new Set());

  useEffect(() => {
    const map = firstLappedAt.current;
    const now = Date.now();

    if (rows.length === 0 || !everyoneOffLapZero(rows)) {
      map.clear();
    } else {
      const leaderLaps = Math.max(...rows.map((r) => r.lapsCompleted));

      // Remove stale entries for cars no longer in the race
      const currentIds = new Set(rows.map((r) => r.entryId));
      for (const id of map.keys()) {
        if (!currentIds.has(id)) map.delete(id);
      }

      for (const row of rows) {
        if (row.lapsCompleted < leaderLaps) {
          if (!map.has(row.entryId)) map.set(row.entryId, now);
        } else {
          map.delete(row.entryId);
        }
      }
    }

    // The set is updated from timers rather than in the effect itself: straight away, then again when the next
    // lapped car reaches the debounce, so its badge appears even if the timing doesn't change meanwhile
    let timer: ReturnType<typeof setTimeout>;
    const evaluate = () => {
      const at = Date.now();
      const newSet = new Set<number>();
      let nextDeadline: number | null = null;
      for (const [entryId, since] of map) {
        const deadline = since + debounceMs;
        if (at >= deadline) newSet.add(entryId);
        else if (nextDeadline === null || deadline < nextDeadline) nextDeadline = deadline;
      }
      setLappedSet((prev) =>
        prev.size === newSet.size && [...newSet].every((id) => prev.has(id)) ? prev : newSet,
      );
      if (nextDeadline !== null) timer = setTimeout(evaluate, nextDeadline - at);
    };
    timer = setTimeout(evaluate, 0);
    return () => clearTimeout(timer);
  }, [rows, debounceMs]);

  return lappedSet;
}
