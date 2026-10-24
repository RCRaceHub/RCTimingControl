import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How many recent fresh passings the estimate is taken from. */
const SAMPLES = 20;

/**
 * Works out how far this device's clock is from the timing laptop's (#479), from the passings that arrive while
 * the board is open. A passing is stamped with the laptop's clock at the line and reaches the board a moment
 * later, so {@code passing - local now} is the offset less the delivery delay; the largest recent sample is the
 * one with the least delay. Until a fresh passing has arrived the estimate is 0.
 *
 * <p>The rows a board first loads can be minutes old, so they are only remembered. A row counts as fresh when
 * its passing time differs from the one last seen for that car. Not thread-safe, which a browser tab does not need.
 */
export class ClockOffsetEstimator {
  private readonly lastSeen = new Map<number, number>();
  private readonly samples: number[] = [];
  private primed = false;

  /**
   * Takes the latest rows and returns the server's clock minus this device's, in milliseconds.
   *
   * @param rows the live timing rows as just received
   * @param localNowMs this device's clock now, in UTC milliseconds
   */
  observe(rows: LiveTimingRowDto[], localNowMs: number): number {
    for (const row of rows) {
      if (this.primed && row.lastPassingTimeMs && this.lastSeen.get(row.entryId) !== row.lastPassingTimeMs) {
        this.samples.push(row.lastPassingTimeMs - localNowMs);
        if (this.samples.length > SAMPLES) this.samples.shift();
      }
      this.lastSeen.set(row.entryId, row.lastPassingTimeMs);
    }
    if (rows.length > 0) this.primed = true;
    return this.samples.length > 0 ? Math.max(...this.samples) : 0;
  }
}
