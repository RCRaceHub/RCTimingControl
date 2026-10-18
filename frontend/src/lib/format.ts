import type { RunOrderItemDto } from './raceControlApi';

/** A lap or race time: m:ss.mmm over a minute, else s.mmm; '—' when missing or zero. */
export function fmtMs(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms <= 0) return '—';
  const total = Math.floor(ms);
  const m = Math.floor(total / 60000);
  const secs = Math.floor((total % 60000) / 1000);
  const millis = String(total % 1000).padStart(3, '0');
  return m > 0 ? `${m}:${String(secs).padStart(2, '0')}.${millis}` : `${secs}.${millis}`;
}

type RaceRound = Pick<RunOrderItemDto, 'roundType' | 'roundNumber' | 'finalLetter'>;

/** The round a race belongs to: "Final A", "Qualifier 2" ("Q2" when short) or "Practice". */
export function roundName(race: RaceRound, short = false): string {
  if (race.roundType === 'FINAL' && race.finalLetter === 'Z') return 'Zombie round';
  if (race.roundType === 'FINAL') return `Final${race.finalLetter ? ` ${race.finalLetter}` : ''}`;
  if (race.roundType === 'QUALIFIER') return short ? `Q${race.roundNumber}` : `Qualifier ${race.roundNumber}`;
  return 'Practice';
}
