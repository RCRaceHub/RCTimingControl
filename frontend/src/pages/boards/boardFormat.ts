import { fmtMs, gapLabel } from '@/lib/format';
import type { HighlightMark, RaceHighlightsDto, TickerDto } from '@/lib/boardsApi';
import type { GapMode } from '@/hooks/useGapView';
import { DEFAULT_CLOSE_MS } from '@/lib/lapProgress';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How often the boards re-poll their public endpoints. */
export const BOARD_POLL_MS = 5000;

/** Reads the optional `?event=ID` board parameter; anything that isn't a positive id is ignored. */
export function parseEventParam(value: string | null): number | null {
  if (!value) return null;
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

const DEFAULT_TOP = 30;
const MAX_TOP = 30;

/** Reads the optional `?top=N` parameter of the live top board: how many places to show, thirty unless asked. */
export function parseTopParam(value: string | null): number {
  const top = Number(value);
  return Number.isInteger(top) && top > 0 ? Math.min(top, MAX_TOP) : DEFAULT_TOP;
}

/** The most seconds `close=` may ask for on a board address. */
const LIVE_RACE_MAX_CLOSE_S = 60;

/**
 * Reads {@code ?legend=}: {@code hide} leaves the colour key out and {@code show} puts it in; anything else takes
 * {@code shownByDefault}, which is on for the boards and off for the streaming overlay.
 */
export function parseLegendParam(value: string | null, shownByDefault: boolean): boolean {
  const v = value?.toLowerCase();
  return v === 'hide' ? false : v === 'show' ? true : shownByDefault;
}

/**
 * The live race board's options from its query string: {@code bars=hide} leaves out the lap bars,
 * {@code arrows=hide} the places-gained arrows, {@code predict=hide} the predicted laps,
 * {@code battles=hide} the battles strip (#505), {@code legend=hide} the colour key, and {@code close=SECONDS} sets how near the car ahead counts as
 * close (0 switches the tint off). A close value that is empty or not a number takes {@code defaultCloseMs}, the club's setting.
 */
export function parseLiveRaceOptions(params: URLSearchParams, defaultCloseMs: number = DEFAULT_CLOSE_MS) {
  const raw = params.get('close');
  const seconds = raw === null || raw.trim() === '' ? NaN : Number(raw);
  return {
    showBars: params.get('bars')?.toLowerCase() !== 'hide',
    showArrows: params.get('arrows')?.toLowerCase() !== 'hide',
    showPredicted: params.get('predict')?.toLowerCase() !== 'hide',
    showBattles: params.get('battles')?.toLowerCase() !== 'hide',
    showLegend: parseLegendParam(params.get('legend'), true),
    closeMs: Number.isFinite(seconds)
      ? Math.min(LIVE_RACE_MAX_CLOSE_S, Math.max(0, seconds)) * 1000
      : defaultCloseMs,
  };
}

/** What the overlay's ticker can scroll; `?ticker=` names the ones to show, `?ticker=hide` shows none. */
export const TICKER_ITEMS = ['next', 'drivers', 'marshals', 'tq', 'fastest', 'gain', 'loss', 'pace'] as const;
export type TickerItem = (typeof TICKER_ITEMS)[number];

function parseTickerItems(value: string | null): Set<TickerItem> {
  if (value === null || value === '') return new Set(TICKER_ITEMS);
  return new Set(TICKER_ITEMS.filter((item) => value.split(',').includes(item)));
}

/** The tower's gap from `?gap=`: the interval to the car ahead unless asked for the leader gap or the two in turn. */
function parseGapMode(value: string | null): GapMode {
  const mode = value?.toLowerCase();
  return mode === 'leader' || mode === 'auto' ? mode : 'int';
}

/** Seconds each gap stays up in auto mode when `gapevery=` is not given. */
export const DEFAULT_GAP_EVERY_S = 6;
/** The most seconds `gapevery=` may ask for. */
export const GAP_EVERY_LIMIT = 60;

/** Reads `?gapevery=SECONDS`: how long each gap shows in auto mode, 1 to 60, six unless asked. */
function parseGapEvery(value: string | null): number {
  const s = Number(value);
  return value !== null && value.trim() !== '' && Number.isFinite(s) && s >= 1
    ? Math.min(GAP_EVERY_LIMIT, Math.round(s))
    : DEFAULT_GAP_EVERY_S;
}

/**
 * Reads `?separator=`: the address of a small image to put between ticker items instead of the diamond. Only a
 * web address or a path on this server is taken (never `javascript:`, `data:` or a protocol-relative address);
 * anything else, or no value, keeps the diamond (null).
 */
export function parseSeparatorImage(value: string | null): string | null {
  const v = value?.trim();
  if (!v || v.toLowerCase() === 'diamond') return null;
  return /^https?:\/\/\S+$/i.test(v) || /^\/(?![/\\])\S*$/.test(v) ? v : null;
}

const OVERLAY_DEFAULT_TOP = 10;
const OVERLAY_MAX_TOP = 40;

/** The streaming overlay's options from its query string; anything unrecognised falls back to the default. */
export function parseOverlayOptions(params: URLSearchParams, defaultCloseMs: number = DEFAULT_CLOSE_MS) {
  const top = Number(params.get('top'));
  return {
    eventId: parseEventParam(params.get('event')),
    top: Number.isInteger(top) && top > 0 ? Math.min(top, OVERLAY_MAX_TOP) : OVERLAY_DEFAULT_TOP,
    showClass: params.get('class') !== 'hide',
    theme: params.get('theme') === 'light' ? ('light' as const) : ('dark' as const),
    showChart: params.get('chart') !== 'hide',
    tickerItems: parseTickerItems(params.get('ticker')),
    showSponsors: params.get('sponsors')?.toLowerCase() !== 'hide',
    layout: params.get('layout')?.toLowerCase() === 'table' ? ('table' as const) : ('tower' as const),
    showQualifying: params.get('qual')?.toLowerCase() !== 'hide',
    gap: parseGapMode(params.get('gap')),
    gapEverySeconds: parseGapEvery(params.get('gapevery')),
    separatorImage: parseSeparatorImage(params.get('separator')),
    ...parseLiveRaceOptions(params, defaultCloseMs),
    // After the spread on purpose: the overlay's key is off unless asked, the boards' is on
    showLegend: parseLegendParam(params.get('legend'), false),
  };
}

/** The gap to the leader a tower row shows: laps down, or the time behind the leader; a dash for the leader. */
export function leaderGapText(row: LiveTimingRowDto): string {
  return gapLabel(row.position, row.lapsDown, row.gapToLeaderMs);
}

/** The gap a tower row shows: laps down, or the time behind the car ahead; a dash for the leader and for no gap yet. */
export function gapText(row: LiveTimingRowDto): string {
  return gapLabel(row.position, row.intervalLapsDown, row.gapToAheadMs);
}

/** Race time as m:ss, rounded down to the second. */
export function fmtClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

/** The quickest lap among the cars on track, with whose it is; null before anyone has a timed lap. */
export function heatBestLap(rows: LiveTimingRowDto[]): { lapMs: number; driverName: string } | null {
  let best: { lapMs: number; driverName: string } | null = null;
  for (const row of rows) {
    if (row.bestLapMs !== null && row.bestLapMs > 0 && (best === null || row.bestLapMs < best.lapMs)) {
      best = { lapMs: row.bestLapMs, driverName: row.driverName };
    }
  }
  return best;
}

/** The round's fastest lap: the quicker of the running heat's best and the best of the round's other finished heats. */
export function roundBestLap(
  heatBest: { lapMs: number; driverName: string } | null,
  round: { lapMs: number | null; driverName: string | null } | undefined,
): { lapMs: number; driverName: string } | null {
  return round?.lapMs != null && round.driverName && (heatBest === null || round.lapMs < heatBest.lapMs)
    ? { lapMs: round.lapMs, driverName: round.driverName }
    : heatBest;
}

/** Most names read out in one ticker item, so a big grid doesn't take a minute to scroll past. */
const TICKER_MAX_NAMES = 12;

function nameList(names: string[]): string {
  return names.length <= TICKER_MAX_NAMES
    ? names.join(', ')
    : `${names.slice(0, TICKER_MAX_NAMES).join(', ')} and ${names.length - TICKER_MAX_NAMES} more`;
}

function sameMark(a: HighlightMark | null, b: HighlightMark | null): boolean {
  return a !== null && b !== null && a.driverName === b.driverName && a.value === b.value;
}

/** A standout for the heat and, where the round has a different one, for the round; either may be missing. */
function markSegments(
  heat: HighlightMark | null,
  round: HighlightMark | null,
  text: (label: string, mark: HighlightMark) => string,
): string[] {
  const segments: string[] = [];
  if (heat) segments.push(text('this heat', heat));
  if (round && !sameMark(heat, round)) segments.push(text('the round', round));
  return segments;
}

/** The ticker's items, in the order they scroll, for the parts the official switched on and that have something. */
export function tickerSegments(
  items: Set<TickerItem>,
  ticker: TickerDto | undefined,
  roundBest: { lapMs: number; driverName: string } | null,
  highlights?: RaceHighlightsDto,
): string[] {
  const segments: string[] = [];
  if (items.has('next') && ticker?.nextRace) segments.push(`Next: ${ticker.nextRace}`);
  if (items.has('drivers') && ticker && ticker.nextDrivers.length > 0) {
    segments.push(`On the grid: ${nameList(ticker.nextDrivers)}`);
  }
  if (items.has('marshals') && ticker && ticker.marshals.length > 0) {
    segments.push(`Marshals: ${nameList(ticker.marshals)}`);
  }
  if (items.has('tq') && ticker) {
    for (const top of ticker.topQualifiers) {
      const lap = top.bestLapMs ? ` (${fmtMs(top.bestLapMs)})` : '';
      segments.push(`TQ ${top.className}: ${top.driverName}${lap}`);
    }
  }
  if (items.has('fastest') && roundBest) {
    segments.push(`Fastest lap of the round: ${fmtMs(roundBest.lapMs)} · ${roundBest.driverName}`);
  }
  if (highlights) {
    const places = (verb: string, sign: string) => (label: string, mark: HighlightMark) =>
      `Most places ${verb} in ${label}: ${mark.driverName} (${sign}${mark.value})`;
    const pace = (laps: number) => (label: string, mark: HighlightMark) =>
      `Best ${laps} lap average in ${label}: ${fmtMs(mark.value)} · ${mark.driverName}`;
    if (items.has('gain')) segments.push(...markSegments(highlights.heat.gained, highlights.round.gained, places('gained', '+')));
    if (items.has('loss')) segments.push(...markSegments(highlights.heat.lost, highlights.round.lost, places('lost', '−')));
    if (items.has('pace')) {
      segments.push(...markSegments(highlights.heat.avg3, highlights.round.avg3, pace(3)));
      segments.push(...markSegments(highlights.heat.avg5, highlights.round.avg5, pace(5)));
    }
  }
  return segments;
}

/** Everything the overlay designer (#524) lets an official choose; the overlay reads the same choices from its address. */
export type OverlayDesign = {
  eventId: number | null;
  top: number;
  showClass: boolean;
  theme: 'dark' | 'light';
  showChart: boolean;
  tickerItems: TickerItem[];
  showSponsors: boolean;
  layout: 'tower' | 'table';
  showQualifying: boolean;
  gap: GapMode;
  gapEverySeconds: number;
  /** The address of an image to put between ticker items instead of the diamond; null keeps the diamond. */
  separatorImage: string | null;
  showPredicted: boolean;
  showBars: boolean;
  showArrows: boolean;
  showBattles: boolean;
  /** Show the colour key; off unless asked, as it sits over the video. */
  showLegend: boolean;
  /** Seconds a car may be behind the one ahead and count as close; null takes the club's setting. */
  closeSeconds: number | null;
};

/** The overlay as it opens with no options on its address. */
export const DEFAULT_OVERLAY_DESIGN: OverlayDesign = {
  eventId: null,
  top: OVERLAY_DEFAULT_TOP,
  showClass: true,
  theme: 'dark',
  showChart: true,
  tickerItems: [...TICKER_ITEMS],
  showSponsors: true,
  layout: 'tower',
  showQualifying: true,
  gap: 'int',
  gapEverySeconds: DEFAULT_GAP_EVERY_S,
  separatorImage: null,
  showPredicted: true,
  showBars: true,
  showArrows: true,
  showBattles: true,
  showLegend: false,
  closeSeconds: null,
};

/**
 * An overlay design from a saved theme's JSON: each known field is taken only when it has
 * the right type and value, and anything missing or unknown keeps the default, so a theme from an older or newer
 * build still opens. The event is not part of a theme and comes back null.
 */
export function overlayDesignFromTheme(raw: unknown): OverlayDesign {
  const design: OverlayDesign = { ...DEFAULT_OVERLAY_DESIGN, tickerItems: [...DEFAULT_OVERLAY_DESIGN.tickerItems] };
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return design;
  const r = raw as Record<string, unknown>;
  for (const key of ['showClass', 'showChart', 'showSponsors', 'showQualifying', 'showPredicted', 'showBars',
    'showArrows', 'showBattles', 'showLegend'] as const) {
    if (typeof r[key] === 'boolean') design[key] = r[key];
  }
  if (r.theme === 'light' || r.theme === 'dark') design.theme = r.theme;
  if (r.layout === 'tower' || r.layout === 'table') design.layout = r.layout;
  if (r.gap === 'int' || r.gap === 'leader' || r.gap === 'auto') design.gap = r.gap;
  if (typeof r.top === 'number' && Number.isInteger(r.top) && r.top > 0) design.top = Math.min(r.top, OVERLAY_MAX_TOP);
  if (typeof r.gapEverySeconds === 'number' && Number.isInteger(r.gapEverySeconds) && r.gapEverySeconds > 0) {
    design.gapEverySeconds = Math.min(r.gapEverySeconds, GAP_EVERY_LIMIT);
  }
  if (typeof r.closeSeconds === 'number' && Number.isFinite(r.closeSeconds) && r.closeSeconds >= 0) {
    design.closeSeconds = r.closeSeconds;
  }
  if (typeof r.separatorImage === 'string') design.separatorImage = parseSeparatorImage(r.separatorImage);
  if (Array.isArray(r.tickerItems)) {
    design.tickerItems = TICKER_ITEMS.filter((item) => (r.tickerItems as unknown[]).includes(item));
  }
  return design;
}

/** The theme's design as saved: everything the designer chooses except which event it follows. */
export function themeDesignToSave(design: OverlayDesign): Omit<OverlayDesign, 'eventId'> {
  const rest: Partial<OverlayDesign> = { ...design };
  delete rest.eventId;
  return rest as Omit<OverlayDesign, 'eventId'>;
}

/** The most places the designer offers; the overlay itself caps `top` here. */
export const OVERLAY_TOP_LIMIT = OVERLAY_MAX_TOP;

/**
 * The query string ("" or "?a=b&...") that opens the overlay as designed. Only choices that differ from the
 * overlay's defaults are written, so a stock design is the bare address and the addresses stay short for OBS.
 * {@link parseOverlayOptions} reads back what this writes.
 */
export function overlayQuery(design: OverlayDesign): string {
  const q = new URLSearchParams();
  if (design.eventId !== null) q.set('event', String(design.eventId));
  if (design.top !== OVERLAY_DEFAULT_TOP) q.set('top', String(design.top));
  if (!design.showClass) q.set('class', 'hide');
  if (design.theme === 'light') q.set('theme', 'light');
  if (!design.showChart) q.set('chart', 'hide');
  if (design.tickerItems.length === 0) q.set('ticker', 'hide');
  else if (design.tickerItems.length < TICKER_ITEMS.length) {
    q.set('ticker', TICKER_ITEMS.filter((item) => design.tickerItems.includes(item)).join(','));
  }
  if (!design.showSponsors) q.set('sponsors', 'hide');
  if (parseSeparatorImage(design.separatorImage) !== null) q.set('separator', design.separatorImage!.trim());
  if (design.layout === 'table') q.set('layout', 'table');
  if (!design.showQualifying) q.set('qual', 'hide');
  if (design.gap !== 'int') q.set('gap', design.gap);
  if (design.gap === 'auto' && design.gapEverySeconds !== DEFAULT_GAP_EVERY_S) {
    q.set('gapevery', String(design.gapEverySeconds));
  }
  if (!design.showPredicted) q.set('predict', 'hide');
  if (!design.showBars) q.set('bars', 'hide');
  if (!design.showArrows) q.set('arrows', 'hide');
  if (!design.showBattles) q.set('battles', 'hide');
  if (design.showLegend) q.set('legend', 'show');
  if (design.closeSeconds !== null) q.set('close', String(design.closeSeconds));
  const text = q.toString().replace(/%2C/g, ',');
  return text ? `?${text}` : '';
}
