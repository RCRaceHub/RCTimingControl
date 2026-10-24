import type { PracticeTimingRowDto } from '@/lib/practiceApi';

/** The first word of a name, for a call that names drivers briefly (#482). */
export function firstNameOf(name: string): string {
  return name.trim().split(/\s+/)[0] ?? '';
}

/** A lap time as it is said: "12.34" seconds, or "1 minute 12.34" from a minute up. Hundredths are enough to hear. */
export function spokenLapTime(ms: number): string {
  const hundredths = Math.round(ms / 10);
  const minutes = Math.floor(hundredths / 6000);
  const seconds = ((hundredths % 6000) / 100).toFixed(2);
  return minutes > 0 ? `${minutes} minute${minutes === 1 ? '' : 's'} ${seconds}` : seconds;
}

/** What is said as a driver finishes a lap: their name (or first name) and the lap time. */
export function lapCallText(row: Pick<PracticeTimingRowDto, 'racerName' | 'isUnknown'>, lastLapMs: number,
                            firstNameOnly: boolean): string {
  const name = row.isUnknown ? 'Unknown' : firstNameOnly ? firstNameOf(row.racerName) : row.racerName.trim();
  return `${name}, ${spokenLapTime(lastLapMs)}`;
}

/**
 * The laps completed between two views of the live table, each as the text to say: a driver whose lap count went up
 * since {@code before} (their lap counts by transponder). A driver not in {@code before} counts from none, so a
 * driver's first lap in the session is called.
 */
export function newLapCalls(before: Map<string, number>, rows: PracticeTimingRowDto[],
                            firstNameOnly: boolean): string[] {
  const calls: string[] = [];
  for (const row of rows) {
    const previous = before.get(row.transponderNumber) ?? 0;
    if (row.laps > previous && row.lastLapMs != null && row.lastLapMs > 0) {
      calls.push(lapCallText(row, row.lastLapMs, firstNameOnly));
    }
  }
  return calls;
}
