import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How far past its average lap a car is before its lap bar turns red, in milliseconds. */
export const LATE_AFTER_AVERAGE_MS = 1000;
/** Cars this close behind the one ahead, in milliseconds, are shown as running close. */
export const DEFAULT_CLOSE_MS = 1000;

/** Where a car is in its current lap, judged against its own average lap (#479). */
export interface LapProgress {
  /** The share of an average lap run so far, 0 to 1 (the bar stays full once it is past the average). */
  fraction: number;
  /**
   * {@code under} while the lap is still shorter than the car's average, {@code at} from the average until
   * 1 s past it, then {@code late}.
   */
  state: 'under' | 'at' | 'late';
}

/**
 * Estimates how far a car has got round its lap from the time since it last crossed the line. Null when it has
 * no average lap yet (the first laps) or has taken the flag.
 *
 * @param row the car's live timing row
 * @param nowMs the current time as the server's clock reads it, in UTC milliseconds
 */
export function lapProgress(row: LiveTimingRowDto, nowMs: number): LapProgress | null {
  const average = row.avgLapMs;
  if (row.finished || !average || average <= 0 || !row.lastPassingTimeMs) return null;
  const elapsed = Math.max(0, nowMs - row.lastPassingTimeMs);
  const state = elapsed < average ? 'under' : elapsed < average + LATE_AFTER_AVERAGE_MS ? 'at' : 'late';
  return { fraction: Math.min(1, elapsed / average), state };
}

/**
 * Whether a car is running close behind the one in front: on the same lap and within {@code thresholdMs}. The
 * leader and a car a lap or more down are never close. A threshold of 0 or less switches the flag off.
 */
export function isClose(row: LiveTimingRowDto, thresholdMs: number): boolean {
  if (thresholdMs <= 0 || row.position === 1 || row.intervalLapsDown > 0 || row.finished) return false;
  return row.gapToAheadMs !== null && row.gapToAheadMs <= thresholdMs;
}
