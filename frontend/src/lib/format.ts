import type { RunOrderItemDto } from './raceControlApi';

// Whether the club cuts times to hundredths of a second. Set once from the club's settings by TimePrecisionSync, so
// every screen that formats a time agrees without each one asking for it.
let hundredths = false;

/** Sets whether times are shown to hundredths (truncated, never rounded) rather than thousandths. */
export function setTimeInHundredths(on: boolean): void {
  hundredths = on;
}

/**
 * A lap or race time: m:ss.mmm over a minute, else s.mmm; '—' when missing or zero. When the club cuts times to
 * hundredths it is m:ss.hh or s.hh, truncated, so a time never reads faster or slower than the rank it was given.
 */
export function fmtMs(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms <= 0) return '—';
  const total = Math.floor(ms);
  const m = Math.floor(total / 60000);
  const secs = Math.floor((total % 60000) / 1000);
  const fraction = hundredths
    ? String(Math.floor((total % 1000) / 10)).padStart(2, '0')
    : String(total % 1000).padStart(3, '0');
  return m > 0 ? `${m}:${String(secs).padStart(2, '0')}.${fraction}` : `${secs}.${fraction}`;
}

/**
 * A gap as every screen shows it: "+1 lap" or "+2 laps" for a car laps down, else "+" and the time behind, and a
 * dash for the leader and for a car with no gap yet. Use this on any screen that shows a gap so they all agree.
 */
export function gapLabel(position: number, lapsDown: number, gapMs: number | null | undefined): string {
  if (position === 1) return '—';
  if (lapsDown > 0) return `+${lapsDown} ${lapsDown === 1 ? 'lap' : 'laps'}`;
  return gapMs !== null && gapMs !== undefined ? `+${fmtMs(gapMs)}` : '—';
}

/** The gap to the leader for a finished-race result row, working out the laps down from the leader's lap count. */
export function resultGapLabel(
  row: { position: number; lapsCompleted: number; gapToLeaderMs: number | null },
  leaderLaps: number,
): string {
  return gapLabel(row.position, leaderLaps - row.lapsCompleted, row.gapToLeaderMs);
}

type RaceRound = Pick<RunOrderItemDto, 'roundType' | 'roundNumber' | 'finalLetter'>;

/** The round a race belongs to: "Final A", "Qualifier 2" ("Q2" when short) or "Practice". */
export function roundName(race: RaceRound, short = false): string {
  if (race.roundType === 'FINAL' && race.finalLetter === 'Z') return 'Zombie round';
  if (race.roundType === 'FINAL') return `Final${race.finalLetter ? ` ${race.finalLetter}` : ''}`;
  if (race.roundType === 'QUALIFIER') return short ? `Q${race.roundNumber}` : `Qualifier ${race.roundNumber}`;
  return 'Practice';
}
