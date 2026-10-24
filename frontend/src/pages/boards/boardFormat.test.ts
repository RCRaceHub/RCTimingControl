import { describe, expect, it } from 'vitest';
import { parseLiveRaceOptions } from './boardFormat';

describe('parseLiveRaceOptions', () => {
  const parse = (query: string) => parseLiveRaceOptions(new URLSearchParams(query));

  it('shows the bars and tints cars within a second unless told otherwise', () => {
    expect(parse('')).toEqual({
      showBars: true,
      showArrows: true,
      showPredicted: true,
      showBattles: true,
      closeMs: 1000,
    });
    expect(parse('battles=HIDE').showBattles).toBe(false);
  });

  it('hides the arrows for arrows=hide and the predicted laps for predict=hide', () => {
    expect(parse('arrows=hide').showArrows).toBe(false);
    expect(parse('predict=HIDE').showPredicted).toBe(false);
    expect(parse('arrows=show&predict=show')).toMatchObject({ showArrows: true, showPredicted: true });
  });

  it('hides the bars for bars=hide in any case', () => {
    expect(parse('bars=hide').showBars).toBe(false);
    expect(parse('bars=HIDE').showBars).toBe(false);
    expect(parse('bars=show').showBars).toBe(true);
  });

  it('takes close in seconds, with 0 for off, and falls back to the default for nonsense', () => {
    expect(parse('close=2.5').closeMs).toBe(2500);
    expect(parse('close=0').closeMs).toBe(0);
    expect(parse('close=-3').closeMs).toBe(0);
    expect(parse('close=').closeMs).toBe(1000);
    expect(parse('close=abc').closeMs).toBe(1000);
    expect(parse('close=1e308').closeMs).toBe(60_000);
  });
});
