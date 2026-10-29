import { describe, it, expect } from 'vitest';
import { DEFAULT_OVERLAY_DESIGN, overlayDesignFromTheme, overlayQuery, parseOverlayOptions } from './boardFormat';
import {
  DEFAULT_PLACEMENT, defaultPlacement, parsePlacementParam, partsInZone, placementFromTheme, placementParam,
} from './overlayLayout';

describe('overlay placement', () => {
  it('writes nothing for the default layout', () => {
    expect(placementParam(defaultPlacement())).toBe('');
    expect(overlayQuery(DEFAULT_OVERLAY_DESIGN)).toBe('');
  });

  it('writes only the parts that moved, and reads them back', () => {
    const moved = { ...defaultPlacement(), chart: 'top-right' as const, ticker: 'top-strip' as const };
    expect(placementParam(moved)).toBe('chart:top-right,ticker:top-strip');
    const query = overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, placement: moved });
    expect(query).toBe('?place=chart:top-right,ticker:top-strip');
    expect(parseOverlayOptions(new URLSearchParams(query)).placement).toEqual(moved);
  });

  it('ignores unknown parts and zones', () => {
    expect(parsePlacementParam('nope:top-left,chart:middle,tower:top-right')).toEqual({
      ...DEFAULT_PLACEMENT,
      tower: 'top-right',
    });
    expect(parsePlacementParam(null)).toEqual(DEFAULT_PLACEMENT);
  });

  it('takes a theme without a placement as the default layout', () => {
    expect(overlayDesignFromTheme({ theme: 'light' }).placement).toEqual(DEFAULT_PLACEMENT);
    expect(placementFromTheme([1, 2])).toEqual(DEFAULT_PLACEMENT);
    expect(overlayDesignFromTheme({ placement: { chart: 'top-left', ticker: 7 } }).placement).toEqual({
      ...DEFAULT_PLACEMENT,
      chart: 'top-left',
    });
  });

  it('stacks the sponsor bar and ticker nearest the edge', () => {
    expect(partsInZone(DEFAULT_PLACEMENT, 'bottom-strip')).toEqual(['sponsors', 'ticker']);
    expect(partsInZone({ ...DEFAULT_PLACEMENT, tower: 'bottom-left' }, 'bottom-left')).toEqual(['battles', 'tower']);
  });
});
