import { fmtMs } from '@/lib/format';
import type { TickerDto } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/** How often the boards re-poll their public endpoints. */
export const BOARD_POLL_MS = 5000;

/** Reads the optional `?event=ID` board parameter; anything that isn't a positive id is ignored. */
export function parseEventParam(value: string | null): number | null {
  if (!value) return null;
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

const DEFAULT_TOP = 10;
const MAX_TOP = 30;

/** Reads the optional `?top=N` parameter of the live top board: how many places to show, ten unless asked. */
export function parseTopParam(value: string | null): number {
  const top = Number(value);
  return Number.isInteger(top) && top > 0 ? Math.min(top, MAX_TOP) : DEFAULT_TOP;
}

/** How near the car ahead counts as close on the live race board, until the address says otherwise. */
const LIVE_RACE_DEFAULT_CLOSE_MS = 1000;
const LIVE_RACE_MAX_CLOSE_S = 60;

/**
 * The live race board's options from its query string: {@code bars=hide} leaves out the lap bars, {@code arrows=hide} the places-gained arrows, {@code predict=hide} the predicted laps, and
 * {@code close=SECONDS} sets how near the car ahead counts as close (0 switches the tint off). A close value
 * that is empty or not a number takes the default.
 */
export function parseLiveRaceOptions(params: URLSearchParams) {
  const raw = params.get('close');
  const seconds = raw === null || raw.trim() === '' ? NaN : Number(raw);
  return {
    showBars: params.get('bars')?.toLowerCase() !== 'hide',
    showArrows: params.get('arrows')?.toLowerCase() !== 'hide',
    showPredicted: params.get('predict')?.toLowerCase() !== 'hide',
    closeMs: Number.isFinite(seconds)
      ? Math.min(LIVE_RACE_MAX_CLOSE_S, Math.max(0, seconds)) * 1000
      : LIVE_RACE_DEFAULT_CLOSE_MS,
  };
}

/** What the overlay's ticker can scroll; `?ticker=` names the ones to show, `?ticker=hide` shows none. */
export const TICKER_ITEMS = ['next', 'drivers', 'marshals', 'tq', 'fastest'] as const;
export type TickerItem = (typeof TICKER_ITEMS)[number];

function parseTickerItems(value: string | null): Set<TickerItem> {
  if (value === null || value === '') return new Set(TICKER_ITEMS);
  return new Set(TICKER_ITEMS.filter((item) => value.split(',').includes(item)));
}

const OVERLAY_DEFAULT_TOP = 10;
const OVERLAY_MAX_TOP = 40;

/** The streaming overlay's options from its query string; anything unrecognised falls back to the default. */
export function parseOverlayOptions(params: URLSearchParams) {
  const top = Number(params.get('top'));
  return {
    eventId: parseEventParam(params.get('event')),
    top: Number.isInteger(top) && top > 0 ? Math.min(top, OVERLAY_MAX_TOP) : OVERLAY_DEFAULT_TOP,
    showClass: params.get('class') !== 'hide',
    theme: params.get('theme') === 'light' ? ('light' as const) : ('dark' as const),
    showChart: params.get('chart') !== 'hide',
    tickerItems: parseTickerItems(params.get('ticker')),
    ...parseLiveRaceOptions(params),
  };
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

/** The ticker's items, in the order they scroll, for the parts the official switched on and that have something. */
export function tickerSegments(
  items: Set<TickerItem>,
  ticker: TickerDto | undefined,
  roundBest: { lapMs: number; driverName: string } | null,
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
  return segments;
}
