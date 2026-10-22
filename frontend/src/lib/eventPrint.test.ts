import { describe, it, expect } from 'vitest';
import type { EventResultsRace } from './raceControlApi';
import { finalsInReadingOrder, groupByClass, heatTitle, qualifyingRounds, racesOfRound, startPosition } from './eventPrint';

function race(over: Partial<EventResultsRace>): EventResultsRace {
  return {
    raceId: 1,
    eventClassId: 1,
    className: 'Buggy',
    roundNumber: 1,
    roundType: 'QUALIFIER',
    heatNumber: 1,
    finalLetter: null,
    result: { raceId: 1, raceLabel: '', finishedAt: '', positions: [], lapHistory: [], clubBranding: null },
    startPositions: {},
    ...over,
  };
}

describe('eventPrint', () => {
  it('groups races by class in the order the classes first run', () => {
    const groups = groupByClass([
      race({ raceId: 1, eventClassId: 7, className: 'Truck' }),
      race({ raceId: 2, eventClassId: 3, className: 'Buggy' }),
      race({ raceId: 3, eventClassId: 7, className: 'Truck' }),
    ]);

    expect(groups.map((g) => g.className)).toEqual(['Truck', 'Buggy']);
    expect(groups[0].races.map((r) => r.raceId)).toEqual([1, 3]);
  });

  it('lists the qualifying rounds once each, in order, and picks a round\'s heats', () => {
    const races = [
      race({ raceId: 1, roundNumber: 2 }),
      race({ raceId: 2, roundNumber: 1 }),
      race({ raceId: 3, roundNumber: 2, heatNumber: 2 }),
      race({ raceId: 4, roundType: 'PRACTICE', roundNumber: 1 }),
    ];

    expect(qualifyingRounds(races)).toEqual([1, 2]);
    expect(racesOfRound(races, 'QUALIFIER', 2).map((r) => r.raceId)).toEqual([1, 3]);
  });

  it('reads finals A first and the zombie round last, across finals rounds', () => {
    const finals = finalsInReadingOrder([
      race({ raceId: 1, roundType: 'FINAL', roundNumber: 5, finalLetter: 'B' }),
      race({ raceId: 2, roundType: 'FINAL', roundNumber: 5, finalLetter: 'A' }),
      race({ raceId: 3, roundType: 'FINAL', roundNumber: 5, finalLetter: 'Z' }),
      race({ raceId: 4, roundType: 'FINAL', roundNumber: 4, finalLetter: 'A' }),
      race({ raceId: 5, roundType: 'QUALIFIER' }),
    ]);

    expect(finals.map((r) => r.raceId)).toEqual([4, 2, 1, 3]);
  });

  it('finds a start position by entry id and names the heat', () => {
    const r = race({ startPositions: { '10': 4 } });

    expect(startPosition(r, 10)).toBe(4);
    expect(startPosition(r, 11)).toBeNull();
    expect(heatTitle(r)).toBe('Qualifier 1, heat 1');
    expect(heatTitle(race({ roundType: 'FINAL', finalLetter: 'A' }))).toBe('Final A');
    expect(heatTitle(race({ roundType: 'FINAL', finalLetter: 'Z' }))).toBe('Zombie round');
  });
});
