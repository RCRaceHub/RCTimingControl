import { describe, expect, it } from 'vitest';
import { boardTextZoom } from './BoardShell';

describe('boardTextZoom', () => {
  it('reads the size option and ignores anything else', () => {
    expect(boardTextZoom('')).toBe(1);
    expect(boardTextZoom('?size=large')).toBe(1.25);
    expect(boardTextZoom('?size=XLARGE')).toBe(1.5);
    expect(boardTextZoom('?size=huge')).toBe(1);
  });
});
