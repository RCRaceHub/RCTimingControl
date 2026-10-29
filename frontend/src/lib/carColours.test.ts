import { describe, expect, it } from 'vitest';
import { carColoursBackground, carColoursFor } from './carColours';

describe('carColoursFor', () => {
  it('prefers the entry colours over the class colour', () => {
    expect(carColoursFor(['#ff0000', '#ffffff'], '#0000ff')).toEqual(['#ff0000', '#ffffff']);
  });

  it('falls back to the class colour, then to nothing', () => {
    expect(carColoursFor([], '#0000ff')).toEqual(['#0000ff']);
    expect(carColoursFor(undefined, null)).toEqual([]);
  });
});

describe('carColoursBackground', () => {
  it('is a flat colour for one and stripes for several', () => {
    expect(carColoursBackground(['#ff0000'])).toBe('#ff0000');
    expect(carColoursBackground(['#ff0000', '#ffff00'])).toBe('linear-gradient(90deg, #ff0000 0% 50%, #ffff00 50% 100%)');
  });
});
