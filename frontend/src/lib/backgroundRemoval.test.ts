import { describe, it, expect } from 'vitest';
import { MODEL_SIZE, applyMask, fitWithin, normaliseMask, toModelInput } from './backgroundRemoval';

describe('backgroundRemoval', () => {
  it('turns pixels into planar normalised model input', () => {
    const rgba = new Uint8ClampedArray(MODEL_SIZE * MODEL_SIZE * 4).fill(255);
    const input = toModelInput(rgba);

    expect(input).toHaveLength(3 * MODEL_SIZE * MODEL_SIZE);
    expect(input[0]).toBeCloseTo((1 - 0.485) / 0.229);
    expect(input[MODEL_SIZE * MODEL_SIZE]).toBeCloseTo((1 - 0.456) / 0.224);
  });

  it('stretches the mask to the full 0 to 255 range', () => {
    expect(Array.from(normaliseMask([0.2, 0.4, 0.6]))).toEqual([0, 128, 255]);
  });

  it('keeps a flat mask from dividing by zero', () => {
    expect(Array.from(normaliseMask([0.5, 0.5]))).toEqual([0, 0]);
  });

  it('writes the scaled mask into the alpha channel only', () => {
    // A 2x2 mask (left solid, right clear) over a 4x2 picture of opaque red
    const rgba = new Uint8ClampedArray(4 * 2 * 4);
    for (let i = 0; i < 8; i++) rgba.set([255, 0, 0, 255], i * 4);
    applyMask(rgba, 4, 2, new Uint8ClampedArray([255, 0, 255, 0]), 2);

    expect(rgba[3]).toBe(255);
    expect(rgba[3 * 4 + 3]).toBe(0);
    expect(rgba[0]).toBe(255);
    expect(rgba[1]).toBe(0);
  });

  it('never scales a picture up', () => {
    expect(fitWithin(800, 400, 1600)).toEqual({ width: 800, height: 400 });
    expect(fitWithin(3200, 1600, 1600)).toEqual({ width: 1600, height: 800 });
  });
});
