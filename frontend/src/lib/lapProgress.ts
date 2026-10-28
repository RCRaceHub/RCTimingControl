import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How far past its average lap a car is before its lap bar turns red, in milliseconds, until the club sets another. */
export const LATE_AFTER_AVERAGE_MS = 1000;
/** Cars this close behind the one ahead, in milliseconds, are shown as running close, until the club sets another. */
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
 * @param lateMarginMs how far past its average a lap runs before the bar turns red, the club's setting
 */
export function lapProgress(
  row: LiveTimingRowDto,
  nowMs: number,
  notBeforeMs?: number | null,
  lateMarginMs: number = LATE_AFTER_AVERAGE_MS,
): LapProgress | null {
  const average = row.avgLapMs;
  if (row.finished || !average || !Number.isFinite(average) || !row.lastPassingTimeMs) return null;
  const elapsed = Math.max(0, nowMs - Math.max(row.lastPassingTimeMs, notBeforeMs ?? 0));
  const state = elapsed < average ? 'under' : elapsed < average + lateMarginMs ? 'at' : 'late';
  return { fraction: Math.min(1, elapsed / average), state };
}

/**
 * Whether a car is on pace to make the laps {@link predictedLaps} counts on, which assume it keeps running its
 * average lap: its last lap was no slower than that average, and the lap it is on is not yet running late (the
 * bar has not turned red). Not on pace, and so no icon, for a car with no average yet or one that has finished.
 *
 * @param row the car's live timing row
 * @param nowMs the current time as the timing laptop's clock reads it, in UTC milliseconds
 * @param notBeforeMs when the race was last resumed, in the same clock, as for {@link lapProgress}
 * @param lateMarginMs how far past its average a lap runs before it counts as late, the club's setting
 */
export function onPace(
  row: LiveTimingRowDto,
  nowMs: number,
  notBeforeMs?: number | null,
  lateMarginMs: number = LATE_AFTER_AVERAGE_MS,
): boolean {
  const progress = lapProgress(row, nowMs, notBeforeMs, lateMarginMs);
  if (!progress || progress.state === 'late') return false;
  return row.lastLapMs == null || row.lastLapMs <= row.avgLapMs!;
}

/**
 * Whether every car in the race has had at least one lap counted. Until then the order is only who crossed the
 * line first off the grid, so nobody is lapped yet.
 */
export function everyoneOffLapZero(rows: ReadonlyArray<{ lapsCompleted: number }>): boolean {
  return rows.every((row) => row.lapsCompleted >= 1);
}

/**
 * The rows with no car marked as laps down while any car is still on lap 0, so "+1 lap" and the like wait until
 * the whole field has crossed the line once. Returns the same array when nothing needs holding back.
 */
export function holdBackLapped(rows: LiveTimingRowDto[]): LiveTimingRowDto[] {
  if (everyoneOffLapZero(rows) || !rows.some((row) => row.lapsDown > 0 || row.intervalLapsDown > 0)) return rows;
  return rows.map((row) => (row.lapsDown > 0 || row.intervalLapsDown > 0 ? { ...row, lapsDown: 0, intervalLapsDown: 0 } : row));
}

/**
 * Whether a car is running close behind the one in front: on the same lap and within {@code thresholdMs}. The
 * leader and a car a lap or more down are never close, and nor is a car that has not yet had a lap counted (the
 * field is still bunched up from the start, as with battles). A threshold of 0 or less switches the flag off.
 *
 * <p>This follows the scored order, not the track: the gap is the difference of the two cars' last crossings
 * (time penalties included), so it changes only at the line.
 */
export function isClose(row: LiveTimingRowDto, thresholdMs: number): boolean {
  if (thresholdMs <= 0 || row.position === 1 || row.intervalLapsDown > 0 || row.finished
    || row.lapsCompleted < 1) return false;
  return row.gapToAheadMs !== null && row.gapToAheadMs <= thresholdMs;
}

/**
 * Places a car has gained since the start: positive is up, negative down, null with no grid place (#479). The
 * order only holds cars that have crossed the line, so the grid place is ranked among those same cars (a car
 * from the back that crosses first is not "up" past a pole sitter who has not yet crossed). It moves only as cars
 * cross the line.
 *
 * @param row the car's live timing row
 * @param rows every row of the race, which the grid place is ranked among
 */
export function placesGained(row: LiveTimingRowDto, rows: LiveTimingRowDto[]): number | null {
  if (row.startPosition == null) return null;
  const gridRank = rows.filter((r) => r.startPosition != null && r.startPosition <= row.startPosition!).length;
  return gridRank - row.position;
}

/**
 * The laps a car is on course to have when the race ends, judged from its average lap (#479). A timed race ends
 * for a car on the first crossing after the time is up, so it counts every crossing that falls in the time left
 * plus the one after it. Null with no average lap, no time left to run, or once the car has finished.
 *
 * @param row the car's live timing row
 * @param nowMs the current time as the timing laptop's clock reads it, in UTC milliseconds
 * @param remainingMs the race time left now, in milliseconds
 * @param notBeforeMs when the race was last resumed, in the same clock; the lap is counted from then if the car
 *     has not crossed since, so a stoppage is not counted as lap time
 */
export function predictedLaps(
  row: LiveTimingRowDto,
  nowMs: number,
  remainingMs: number | null,
  notBeforeMs?: number | null,
): number | null {
  const average = row.avgLapMs;
  if (row.finished || remainingMs === null || remainingMs <= 0 || !average || !row.lastPassingTimeMs) return null;
  const sinceCrossing = Math.max(0, nowMs - Math.max(row.lastPassingTimeMs, notBeforeMs ?? 0));
  return row.lapsCompleted + Math.floor((sinceCrossing + remainingMs) / average) + 1;
}
