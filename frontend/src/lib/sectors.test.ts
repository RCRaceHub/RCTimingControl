import { describe, expect, it } from 'vitest';
import { sectorTone, type SectorBoard, type SectorRow } from '@/lib/sectors';

const board: SectorBoard = { sectorCount: 3, overallBestMs: [4000, 4800, null], rows: [] };

function row(lastMs: number[], bestMs: number[]): SectorRow {
  return { entryId: 1, lapNumber: 2, lastMs, bestMs };
}

describe('sectorTone', () => {
  it('marks the fastest time anyone has set', () => {
    expect(sectorTone(row([4000, 5000, 6000], [4000, 5000, 6000]), board, 0)).toBe('overall-best');
  });

  it("marks a car's own best that is not the fastest", () => {
    expect(sectorTone(row([4200, 5000, 6000], [4200, 5000, 6000]), board, 0)).toBe('personal-best');
  });

  it('leaves a slower lap plain', () => {
    expect(sectorTone(row([4500, 5000, 6000], [4200, 4900, 5900]), board, 0)).toBe('normal');
  });

  it('copes with a sector the row has no time for', () => {
    expect(sectorTone(row([4000], [4000]), board, 2)).toBe('normal');
  });
});
