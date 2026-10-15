import type { EntrySearchResult } from '@/lib/raceControlApi';

/** An entry's transponders for display, primary first: "#1234" or "#1234 / #5678". */
export function transponderLabel(entry: EntrySearchResult): string {
  return entry.secondaryTransponderNumber
    ? `#${entry.transponderNumber} / #${entry.secondaryTransponderNumber}`
    : `#${entry.transponderNumber}`;
}
