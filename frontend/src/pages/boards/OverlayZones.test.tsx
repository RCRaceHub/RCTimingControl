import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { OverlayZones } from './OverlayZones';
import { DEFAULT_PLACEMENT } from './overlayLayout';

describe('OverlayZones', () => {
  it('puts each part in its zone, with a shared zone stacked edge first', () => {
    const { container } = render(
      <OverlayZones
        placement={{ ...DEFAULT_PLACEMENT, chart: 'top-right' }}
        blocks={{
          tower: <p>tower</p>,
          chart: <p>chart</p>,
          ticker: <p>ticker</p>,
          sponsors: <p>sponsors</p>,
          battles: null,
        }}
      />,
    );
    const parts = (zone: string) =>
      Array.from(container.querySelectorAll(`[data-zone="${zone}"] [data-part]`)).map((e) => e.getAttribute('data-part'));
    expect(parts('top-left')).toEqual(['tower']);
    expect(parts('top-right')).toEqual(['chart']);
    expect(parts('bottom-strip')).toEqual(['sponsors', 'ticker']);
    expect(parts('bottom-left')).toEqual([]);
  });
});
