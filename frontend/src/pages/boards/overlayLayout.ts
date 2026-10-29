// Where each part of the streaming overlay sits on the screen (#667). The overlay is cut into eight zones, and an
// official drops each part into one in the Overlay Designer. Parts that share a zone stack. The choice is saved with
// a theme and written to the overlay's address as `?place=chart:top-right,...`, listing only parts moved from their
// default zone, so an unmoved layout adds nothing to the address.

/** The parts of the overlay that can be placed. */
export const OVERLAY_COMPONENTS = ['tower', 'battles', 'chart', 'lowerThird', 'ticker', 'sponsors'] as const;
export type OverlayComponent = (typeof OVERLAY_COMPONENTS)[number];

/** The areas of the screen a part can be dropped into: two full-width strips and, between them, six cells. */
export const OVERLAY_ZONES = [
  'top-strip', 'top-left', 'top-center', 'top-right', 'bottom-left', 'bottom-center', 'bottom-right', 'bottom-strip',
] as const;
export type OverlayZone = (typeof OVERLAY_ZONES)[number];

export type OverlayPlacement = Record<OverlayComponent, OverlayZone>;

/** Where each part sits when nothing is moved: the overlay as it looked before the layout could be changed. */
export const DEFAULT_PLACEMENT: OverlayPlacement = {
  tower: 'top-left',
  battles: 'bottom-left',
  chart: 'bottom-right',
  lowerThird: 'bottom-center',
  ticker: 'bottom-strip',
  sponsors: 'bottom-strip',
};

export const COMPONENT_LABELS: Record<OverlayComponent, string> = {
  tower: 'Timing tower',
  battles: 'Battles and fastest laps',
  chart: 'Position chart',
  lowerThird: 'Featured driver',
  ticker: 'Ticker',
  sponsors: 'Sponsor bar',
};

export const ZONE_LABELS: Record<OverlayZone, string> = {
  'top-strip': 'Top strip',
  'top-left': 'Top left',
  'top-center': 'Top centre',
  'top-right': 'Top right',
  'bottom-left': 'Bottom left',
  'bottom-center': 'Bottom centre',
  'bottom-right': 'Bottom right',
  'bottom-strip': 'Bottom strip',
};

/** A copy of the default placement, so a caller can change it without touching the shared one. */
export function defaultPlacement(): OverlayPlacement {
  return { ...DEFAULT_PLACEMENT };
}

function isZone(value: unknown): value is OverlayZone {
  return typeof value === 'string' && (OVERLAY_ZONES as readonly string[]).includes(value);
}

function isComponent(value: unknown): value is OverlayComponent {
  return typeof value === 'string' && (OVERLAY_COMPONENTS as readonly string[]).includes(value);
}

/**
 * A placement from a theme's JSON: each known part takes its zone only when the zone is one we know, and every
 * other part keeps its default, so a theme from an older or newer build still opens.
 */
export function placementFromTheme(raw: unknown): OverlayPlacement {
  const placement = defaultPlacement();
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return placement;
  for (const [component, zone] of Object.entries(raw)) {
    if (isComponent(component) && isZone(zone)) placement[component] = zone;
  }
  return placement;
}

/** The `place` address value for a placement ("" when nothing is moved), such as `chart:top-right,ticker:top-strip`. */
export function placementParam(placement: OverlayPlacement): string {
  return OVERLAY_COMPONENTS.filter((c) => placement[c] !== DEFAULT_PLACEMENT[c])
    .map((c) => `${c}:${placement[c]}`)
    .join(',');
}

/** Reads back what {@link placementParam} writes; anything unrecognised keeps the part's default zone. */
export function parsePlacementParam(value: string | null): OverlayPlacement {
  const placement = defaultPlacement();
  for (const pair of (value ?? '').split(',')) {
    const [component, zone] = pair.split(':');
    if (isComponent(component) && isZone(zone)) placement[component] = zone;
  }
  return placement;
}

/** Whether two placements put every part in the same zone. */
export function samePlacement(a: OverlayPlacement, b: OverlayPlacement): boolean {
  return OVERLAY_COMPONENTS.every((c) => a[c] === b[c]);
}

/**
 * The parts in a zone, in the order they stack from the screen edge inwards: the sponsor bar and ticker hug the
 * foot of the page, with everything else above them. The top zones use the same order from the top of the page down.
 */
export function partsInZone(placement: OverlayPlacement, zone: OverlayZone): OverlayComponent[] {
  const edgeFirst: OverlayComponent[] = ['sponsors', 'ticker', 'battles', 'chart', 'lowerThird', 'tower'];
  return edgeFirst.filter((c) => placement[c] === zone);
}
