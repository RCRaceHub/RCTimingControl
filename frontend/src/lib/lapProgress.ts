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
 * @param nowMs the current time as the timing laptop's clock reads it, in UTC milliseconds
 * @param notBeforeMs when the race was last resumed, in the same clock; the lap is counted from then if the car
 *     has not crossed the line since, so a stoppage is not counted against it
 */
export function lapProgress(row: LiveTimingRowDto, nowMs: number, notBeforeMs?: number | null): LapProgress | null {
  const average = row.avgLapMs;
  if (row.finished || !average || !Number.isFinite(average) || !row.lastPassingTimeMs) return null;
  const elapsed = Math.max(0, nowMs - Math.max(row.lastPassingTimeMs, notBeforeMs ?? 0));
  const state = elapsed < average ? 'under' : elapsed < average + LATE_AFTER_AVERAGE_MS ? 'at' : 'late';
  return { fraction: Math.min(1, elapsed / average), state };
}

/**
 * Whether a car is running close behind the one in front: on the same lap and within {@code thresholdMs}. The
 * leader and a car a lap or more down are never close. A threshold of 0 or less switches the flag off.
 *
 * <p>This follows the scored order, not the track: the gap is the difference of the two cars' last crossings
 * (time penalties included), so it changes only at the line.
 */
export function isClose(row: LiveTimingRowDto, thresholdMs: number): boolean {
  if (thresholdMs <= 0 || row.position === 1 || row.intervalLapsDown > 0 || row.finished) return false;
  return row.gapToAheadMs !== null && row.gapToAheadMs <= thresholdMs;
}

/**
 * Places a car has gained since the start: positive is up, negative down, null with no grid place (#479). It
 * compares the grid place with the scored order, so it moves only as cars cross the line.
 */
export function placesGained(row: LiveTimingRowDto): number | null {
  return row.startPosition == null ? null : row.startPosition - row.position;
}

/**
 * The laps a car is on course to have when the race ends, judged from its average lap (#479). A timed race ends
 * for a car on the first crossing after the time is up, so it counts every crossing that falls in the time left
 * plus the one after it. Null with no average lap, no time left to run, or once the car has finished.
 *
 * @param row the car's live timing row
 * @param nowMs the current time as the timing laptop's clock reads it, in UTC milliseconds
 * @param remainingMs the race time left now, in milliseconds
 */
export function predictedLaps(row: LiveTimingRowDto, nowMs: number, remainingMs: number | null): number | null {
  const average = row.avgLapMs;
  if (row.finished || remainingMs === null || remainingMs <= 0 || !average || !row.lastPassingTimeMs) return null;
  const sinceCrossing = Math.max(0, nowMs - row.lastPassingTimeMs);
  return row.lapsCompleted + Math.max(1, Math.ceil((sinceCrossing + remainingMs) / average));
}
