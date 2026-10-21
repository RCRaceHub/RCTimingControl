import type { EventResultsRace } from './raceControlApi';

export type ClassRaces = { eventClassId: number; className: string; races: EventResultsRace[] };

/** The races grouped by class, in the order the classes first appear in the run order. */
export function groupByClass(races: EventResultsRace[]): ClassRaces[] {
  const groups = new Map<number, ClassRaces>();
  for (const race of races) {
    let group = groups.get(race.eventClassId);
    if (!group) {
      group = { eventClassId: race.eventClassId, className: race.className, races: [] };
      groups.set(race.eventClassId, group);
    }
    group.races.push(race);
  }
  return [...groups.values()];
}

/** The qualifying rounds that have a finished race, as round numbers in order. */
export function qualifyingRounds(races: EventResultsRace[]): number[] {
  const rounds = new Set(races.filter((r) => r.roundType === 'QUALIFIER').map((r) => r.roundNumber));
  return [...rounds].sort((a, b) => a - b);
}

/** The heats of one round: by heat number within a class, as the run order already has them. */
export function racesOfRound(races: EventResultsRace[], roundType: EventResultsRace['roundType'], round: number) {
  return races.filter((r) => r.roundType === roundType && r.roundNumber === round);
}

/**
 * The finals of a class: by round, and within a round with the A final first (finals run lowest first, but are read
 * highest first). The zombie round (letter Z) comes after the lettered finals.
 */
export function finalsInReadingOrder(races: EventResultsRace[]): EventResultsRace[] {
  const rank = (letter: string | null) => (letter === null ? 998 : letter === 'Z' ? 999 : letter.charCodeAt(0));
  return races
    .filter((r) => r.roundType === 'FINAL')
    .sort((a, b) => a.roundNumber - b.roundNumber || rank(a.finalLetter) - rank(b.finalLetter) || a.heatNumber - b.heatNumber);
}

/** The grid position a driver started from in a race, or null when none was recorded. */
export function startPosition(race: EventResultsRace, entryId: number): number | null {
  return race.startPositions[String(entryId)] ?? null;
}

/** "Qualifier 2 heat 1", "Final A": what to call a race on a sheet that already shows the class. */
export function heatTitle(race: EventResultsRace): string {
  if (race.roundType === 'FINAL') {
    if (race.finalLetter === 'Z') return 'Zombie round';
    return `Final${race.finalLetter ? ` ${race.finalLetter}` : ''}`;
  }
  const round = race.roundType === 'QUALIFIER' ? `Qualifier ${race.roundNumber}` : 'Practice';
  return `${round}, heat ${race.heatNumber}`;
}
