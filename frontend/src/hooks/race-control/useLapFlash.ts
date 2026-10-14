import { useEffect, useMemo, useRef, useState } from 'react';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

export type FlashColor = 'race-best' | 'personal-best' | 'improving' | 'slow';

/** How long the flash stays on the row before expiring. */
const FLASH_DURATION_MS = 2500;
/** Threshold above server-computed running average that classifies a lap as slow. */
const SLOW_THRESHOLD_MS = 2000;

interface LapHistory {
  lapsCompleted: Map<number, number>;
  lastLapMs: Map<number, number>;
}

interface Flash {
  color: FlashColor;
  /** Goes up with every new lap, so a lap's timer never clears a later lap's flash */
  seq: number;
}

/**
 * The new laps in {@code rows} since {@code history}, and the history including them, or null when nothing in
 * {@code rows} is new.
 */
function classifyNewLaps(rows: LiveTimingRowDto[], history: LapHistory) {
  const lapsCompleted = new Map(history.lapsCompleted);
  const lastLapMs = new Map(history.lastLapMs);
  const newFlashes: Array<[number, FlashColor]> = [];
  let changed = false;

  for (const row of rows) {
    const prevCount = lapsCompleted.get(row.entryId) ?? -1;

    if (row.lapsCompleted > prevCount && row.lastLapMs !== null && row.lastLapMs > 0) {
      const prevLap = lastLapMs.get(row.entryId) ?? null;

      let color: FlashColor;
      if (row.overallFastestLapMs !== null && row.lastLapMs === row.overallFastestLapMs) {
        color = 'race-best';
      } else if (row.lastLapMs === row.bestLapMs) {
        color = 'personal-best';
      } else if (row.avgLapMs !== null && row.lastLapMs > row.avgLapMs + SLOW_THRESHOLD_MS) {
        color = 'slow';
      } else if (prevLap === null || row.lastLapMs <= prevLap) {
        color = 'improving';
      } else {
        color = 'slow';
      }

      newFlashes.push([row.entryId, color]);
    }

    if (lapsCompleted.get(row.entryId) !== row.lapsCompleted) {
      lapsCompleted.set(row.entryId, row.lapsCompleted);
      changed = true;
    }
    if (row.lastLapMs !== null && row.lastLapMs > 0 && lastLapMs.get(row.entryId) !== row.lastLapMs) {
      lastLapMs.set(row.entryId, row.lastLapMs);
      changed = true;
    }
  }

  return changed ? { history: { lapsCompleted, lastLapMs }, newFlashes } : null;
}

/**
 * Detects new laps by watching lapsCompleted and classifies each lap time.
 * Returns a flash colour per entry that expires after FLASH_DURATION_MS.
 *
 *  race-best      — fastest lap of the race across all drivers (purple)
 *  personal-best  — new personal best for this driver, not race fastest (blue)
 *  improving      — faster than or equal to the driver's previous lap (green)
 *  slow           — more than SLOW_THRESHOLD_MS above running average (red)
 */
export function useLapFlash(rows: LiveTimingRowDto[]): Map<number, FlashColor> {
  const [history, setHistory] = useState<LapHistory>(() => ({
    lapsCompleted: new Map(),
    lastLapMs: new Map(),
  }));
  const [flashes, setFlashes] = useState<{ seq: number; byEntry: Map<number, Flash> }>(() => ({
    seq: 0,
    byEntry: new Map(),
  }));
  const timerRef = useRef<Map<number, { seq: number; timer: ReturnType<typeof setTimeout> }>>(new Map());

  // Rows are classified while rendering, not in an effect, so a flash shows in the same render as its lap.
  // The history only changes when a lap count or lap time does, so this settles after one extra render.
  const next = classifyNewLaps(rows, history);
  if (next) {
    setHistory(next.history);
    if (next.newFlashes.length > 0) {
      setFlashes((prev) => {
        let seq = prev.seq;
        const byEntry = new Map(prev.byEntry);
        for (const [id, color] of next.newFlashes) byEntry.set(id, { color, seq: ++seq });
        return { seq, byEntry };
      });
    }
  }

  // Each flash expires FLASH_DURATION_MS after its lap; a new lap restarts the time
  useEffect(() => {
    const timers = timerRef.current;
    for (const [id, flash] of flashes.byEntry) {
      const scheduled = timers.get(id);
      if (scheduled?.seq === flash.seq) continue;
      if (scheduled) clearTimeout(scheduled.timer);
      const timer = setTimeout(() => {
        timers.delete(id);
        setFlashes((prev) => {
          if (prev.byEntry.get(id)?.seq !== flash.seq) return prev;
          const byEntry = new Map(prev.byEntry);
          byEntry.delete(id);
          return { seq: prev.seq, byEntry };
        });
      }, FLASH_DURATION_MS);
      timers.set(id, { seq: flash.seq, timer });
    }
  }, [flashes]);

  useEffect(() => {
    const timers = timerRef.current;
    return () => {
      for (const { timer } of timers.values()) clearTimeout(timer);
    };
  }, []);

  return useMemo(
    () => new Map([...flashes.byEntry].map(([id, flash]) => [id, flash.color])),
    [flashes],
  );
}
