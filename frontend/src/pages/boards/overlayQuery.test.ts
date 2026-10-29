import { describe, it, expect } from 'vitest';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { DEFAULT_OVERLAY_DESIGN, leaderGapText, overlayDesignFromTheme, overlayQuery, themeDesignToSave, parseLiveRaceOptions, parseOverlayOptions, parseSeparatorImage } from './boardFormat';

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

  it('writes the bump line, TQ pace and class tags switches only when off, and reads them back', () => {
    const query = overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, showBumpLine: false, showTqPace: false, showClassTags: false });
    expect(query).toBe('?bump=hide&tqpace=hide&tags=hide');
    expect(parseOverlayOptions(new URLSearchParams(query))).toMatchObject({ showBumpLine: false, showTqPace: false, showClassTags: false });
    expect(parseOverlayOptions(new URLSearchParams('')))
      .toMatchObject({ showBumpLine: true, showTqPace: true, showClassTags: true });
  });

  it('writes the lower third trigger only when it is not both, and reads it back', () => {
    expect(overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, lowerThird: 'director' })).toBe('?lower=director');
    expect(parseOverlayOptions(new URLSearchParams('?lower=battle')).lowerThird).toBe('battle');
    expect(parseOverlayOptions(new URLSearchParams('?lower=off')).lowerThird).toBe('off');
    expect(parseOverlayOptions(new URLSearchParams('?lower=nonsense')).lowerThird).toBe('both');
    expect(parseOverlayOptions(new URLSearchParams('')).lowerThird).toBe('both');
  });

  it('writes contrast=high only when asked, and reads it back', () => {
    expect(overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, highContrast: true })).toBe('?contrast=high');
    expect(parseOverlayOptions(new URLSearchParams('?contrast=high')).highContrast).toBe(true);
  });

  it('writes the colour key only when asked, and the boards default the other way', () => {
    expect(overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, showLegend: true })).toBe('?legend=show');
    expect(parseOverlayOptions(new URLSearchParams('?legend=show')).showLegend).toBe(true);
    expect(parseOverlayOptions(new URLSearchParams('')).showLegend).toBe(false);
    expect(parseLiveRaceOptions(new URLSearchParams('')).showLegend).toBe(true);
    expect(parseLiveRaceOptions(new URLSearchParams('?legend=hide')).showLegend).toBe(false);
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

describe('ticker separator image', () => {
  it('round trips an image address and leaves the diamond out of the query', () => {
    expect(overlayQuery(DEFAULT_OVERLAY_DESIGN)).toBe('');
    const query = overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, separatorImage: '/storage/logo.png' });
    expect(query).toBe('?separator=%2Fstorage%2Flogo.png');
    expect(parseOverlayOptions(new URLSearchParams(query)).separatorImage).toBe('/storage/logo.png');
  });

  it('takes only web addresses and server paths', () => {
    expect(parseSeparatorImage('https://club.example/logo.png')).toBe('https://club.example/logo.png');
    expect(parseSeparatorImage(' diamond ')).toBeNull();
    expect(parseSeparatorImage('javascript:alert(1)')).toBeNull();
    expect(parseSeparatorImage('data:image/png;base64,AAAA')).toBeNull();
    expect(parseSeparatorImage('//evil.example/x.png')).toBeNull();
    expect(parseSeparatorImage(null)).toBeNull();
    expect(overlayQuery({ ...DEFAULT_OVERLAY_DESIGN, separatorImage: 'javascript:alert(1)' })).toBe('');
  });
});

describe('overlayDesignFromTheme', () => {
  it('keeps what a theme sets and defaults the rest, ignoring wrong types and unknown fields', () => {
    const design = overlayDesignFromTheme({
      theme: 'light', layout: 'table', top: 6, showSponsors: false, gap: 'sideways', showChart: 'yes',
      tickerItems: ['next', 'nonsense'], separatorImage: 'javascript:alert(1)', extra: 1,
    });

    expect(design.theme).toBe('light');
    expect(design.layout).toBe('table');
    expect(design.top).toBe(6);
    expect(design.showSponsors).toBe(false);
    expect(design.gap).toBe('int');
    expect(design.showChart).toBe(true);
    expect(design.tickerItems).toEqual(['next']);
    expect(design.separatorImage).toBeNull();
    expect(design.eventId).toBeNull();
  });

  it('gives the default design for something that is not an object', () => {
    expect(overlayDesignFromTheme(null)).toEqual(DEFAULT_OVERLAY_DESIGN);
    expect(overlayDesignFromTheme([1])).toEqual(DEFAULT_OVERLAY_DESIGN);
  });

  it('round-trips through the overlay address', () => {
    const design = { ...DEFAULT_OVERLAY_DESIGN, theme: 'light' as const, top: 6, showSponsors: false };
    expect(overlayDesignFromTheme(themeDesignToSave(design))).toEqual(design);
  });
});

describe('parseSeparatorImage', () => {
  it('refuses a backslash path that browsers read as another host', () => {
    expect(parseSeparatorImage('/\\evil.com/x.png')).toBeNull();
    expect(parseSeparatorImage('//evil.com/x.png')).toBeNull();
    expect(parseSeparatorImage('/storage/x.png')).toBe('/storage/x.png');
  });
});
