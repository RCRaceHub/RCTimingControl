import { describe, it, expect } from 'vitest';
import { lapCallText, newLapCalls, spokenLapTime } from './practiceSpeech';
import { firstNameOf } from './speechNames';
import type { PracticeTimingRowDto } from './practiceApi';

function row(over: Partial<PracticeTimingRowDto>): PracticeTimingRowDto {
  return {
    position: 1, transponderNumber: '1001', userId: null, racerName: 'Alex Rowe', laps: 1, bestLapMs: 12340,
    bestConsecutiveNMs: null, lastLapMs: 12340, runs: 1, isUnknown: false, ...over,
  };
}

describe('practice speech', () => {
  it('says a lap time in hundredths, with minutes from a minute up', () => {
    expect(spokenLapTime(12340)).toBe('12.34');
    expect(spokenLapTime(9996)).toBe('10.00');
    expect(spokenLapTime(72340)).toBe('1 minute 12.34');
    expect(spokenLapTime(132000)).toBe('2 minutes 12.00');
  });

  it('takes the first word as the first name', () => {
    expect(firstNameOf('  Alex  Rowe ')).toBe('Alex');
    expect(firstNameOf('Zoe')).toBe('Zoe');
  });

  it('calls the whole name or the first name, and an unknown transponder just that', () => {
    expect(lapCallText(row({}), 12340, false)).toBe('Alex Rowe, 12.34');
    expect(lapCallText(row({}), 12340, true)).toBe('Alex, 12.34');
    expect(lapCallText(row({ isUnknown: true, racerName: '1001' }), 12340, true)).toBe('Unknown, 12.34');
    expect(lapCallText(row({ racerName: '  ' }), 12340, false)).toBe('Unknown, 12.34');
  });

  it('calls only the drivers whose lap count went up', () => {
    const before = new Map([['1001', 3], ['1002', 2]]);
    const rows = [
      row({ transponderNumber: '1001', laps: 4, lastLapMs: 11000 }),
      row({ transponderNumber: '1002', racerName: 'Zoe Hart', laps: 2 }),
      row({ transponderNumber: '1003', racerName: 'Sam Lee', laps: 1, lastLapMs: 15000 }),
    ];

    expect(newLapCalls(before, rows, true)).toEqual(['Alex, 11.00', 'Sam, 15.00']);
  });
});
