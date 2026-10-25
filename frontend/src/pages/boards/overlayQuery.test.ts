import { describe, it, expect } from 'vitest';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { DEFAULT_OVERLAY_DESIGN, leaderGapText, overlayQuery, parseOverlayOptions } from './boardFormat';

describe('overlayQuery', () => {
  it('writes nothing for the default design', () => {
    expect(overlayQuery(DEFAULT_OVERLAY_DESIGN)).toBe('');
  });

  it('writes only what differs, and the overlay reads it back', () => {
    const query = overlayQuery({
      ...DEFAULT_OVERLAY_DESIGN,
      eventId: 7, top: 8, theme: 'light', layout: 'table', showSponsors: false, showChart: false,
      tickerItems: ['next', 'tq'], closeSeconds: 2,
    });
    expect(query).toBe('?event=7&top=8&theme=light&chart=hide&ticker=next,tq&sponsors=hide&layout=table&close=2');
    const read = parseOverlayOptions(new URLSearchParams(query));
    expect(read).toMatchObject({
      eventId: 7, top: 8, theme: 'light', layout: 'table', showSponsors: false, showChart: false, closeMs: 2000,
    });
    expect([...read.tickerItems]).toEqual(['next', 'tq']);
  });

  it('hides the whole ticker when no item is chosen', () => {
    const query = overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, tickerItems: [] });
    expect(query).toBe('?ticker=hide');
    expect(parseOverlayOptions(new URLSearchParams(query)).tickerItems.size).toBe(0);
  });

  it('writes the gap mode and its period, and reads them back', () => {
    const query = overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, gap: 'auto', gapEverySeconds: 8 });
    expect(query).toBe('?gap=auto&gapevery=8');
    expect(parseOverlayOptions(new URLSearchParams(query))).toMatchObject({ gap: 'auto', gapEverySeconds: 8 });
    expect(overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, gap: 'leader' })).toBe('?gap=leader');
    expect(parseOverlayOptions(new URLSearchParams('gap=nonsense&gapevery=0')))
      .toMatchObject({ gap: 'int', gapEverySeconds: 6 });
  });

  it('shows laps down in the leader gap', () => {
    const lapped = { position: 3, lapsDown: 2, gapToLeaderMs: null } as LiveTimingRowDto;
    expect(leaderGapText(lapped)).toBe('+2 laps');
    expect(leaderGapText({ ...lapped, lapsDown: 0, gapToLeaderMs: 3_400 })).toMatch(/^\+/);
    expect(leaderGapText({ ...lapped, position: 1 })).toBe('—');
  });
});
