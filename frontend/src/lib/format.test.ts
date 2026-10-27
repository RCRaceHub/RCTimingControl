import { describe, it, expect } from 'vitest';
import { fmtMs, gapLabel, resultGapLabel, roundName } from './format';

describe('fmtMs', () => {
  it('shows a dash for missing or zero times', () => {
    expect(fmtMs(null)).toBe('—');
    expect(fmtMs(undefined)).toBe('—');
    expect(fmtMs(0)).toBe('—');
  });

  it('shows seconds and milliseconds under a minute', () => {
    expect(fmtMs(9050)).toBe('9.050');
  });

  it('shows minutes over a minute', () => {
    expect(fmtMs(65_007)).toBe('1:05.007');
    expect(fmtMs(300_000)).toBe('5:00.000');
  });
});

describe('roundName', () => {
  it('names finals with their letter', () => {
    expect(roundName({ roundType: 'FINAL', roundNumber: 1, finalLetter: 'A' })).toBe('Final A');
  });

  it('names qualifiers long or short', () => {
    const q2 = { roundType: 'QUALIFIER', roundNumber: 2, finalLetter: null } as const;
    expect(roundName(q2)).toBe('Qualifier 2');
    expect(roundName(q2, true)).toBe('Q2');
  });
});

describe('gapLabel', () => {
  it('shows a dash for the leader and for no gap yet', () => {
    expect(gapLabel(1, 0, null)).toBe('—');
    expect(gapLabel(2, 0, null)).toBe('—');
  });

  it('shows laps down in preference to a time', () => {
    expect(gapLabel(3, 1, 1234)).toBe('+1 lap');
    expect(gapLabel(4, 2, null)).toBe('+2 laps');
  });

  it('shows the time behind with a plus', () => {
    expect(gapLabel(2, 0, 1234)).toBe('+1.234');
  });
});

describe('resultGapLabel', () => {
  it('works out laps down from the leader and ignores the missing time', () => {
    expect(resultGapLabel({ position: 5, lapsCompleted: 9, gapToLeaderMs: null }, 10)).toBe('+1 lap');
    expect(resultGapLabel({ position: 2, lapsCompleted: 10, gapToLeaderMs: 2500 }, 10)).toBe('+2.500');
  });
});
