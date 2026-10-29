/** The icons that can sit beside a driver's name on the timing screens, each of which can be switched off (#696). */
export const ROW_ICONS = ['pace', 'flying', 'lap', 'close', 'lapped', 'penalty', 'signal'] as const;
export type RowIcon = (typeof ROW_ICONS)[number];

/** How each icon is named where it is switched on and off. */
export const ROW_ICON_LABELS: Record<RowIcon, string> = {
  pace: 'On and off pace thumbs',
  flying: 'Flying lap flame',
  lap: 'Lap flash icons (fastest, best, up, slow)',
  close: 'Close to the car ahead',
  lapped: 'Laps down badge',
  penalty: 'Time penalty',
  signal: 'No signal from the decoder',
};

/** The streaming overlay sits over the video, so it starts without the pace thumbs; the boards and race control show every icon. */
export const OVERLAY_DEFAULT_ROW_ICONS: readonly RowIcon[] = ROW_ICONS.filter((icon) => icon !== 'pace');


/**
 * Reads {@code ?icons=}: the icons to show, comma separated, or {@code hide} for none. Absent or empty takes
 * {@code defaults}. Names that are not icons are ignored.
 */
export function parseRowIconsParam(value: string | null, defaults: readonly RowIcon[]): RowIcon[] {
  const text = value?.trim().toLowerCase() ?? '';
  if (text === '') return [...defaults];
  if (text === 'hide') return [];
  const named = text.split(',');
  return ROW_ICONS.filter((icon) => named.includes(icon));
}

/** The {@code icons=} value that shows exactly {@code shown} when {@code defaults} would not, else null to leave it off the address. */
export function rowIconsParam(shown: readonly RowIcon[], defaults: readonly RowIcon[]): string | null {
  const same = shown.length === defaults.length && defaults.every((icon) => shown.includes(icon));
  if (same) return null;
  return shown.length === 0 ? 'hide' : ROW_ICONS.filter((icon) => shown.includes(icon)).join(',');
}

/** Every icon except the ones the club has hidden; names that are not icons are ignored. */
export function rowIconsWithout(hidden: readonly string[] = []): RowIcon[] {
  return ROW_ICONS.filter((icon) => !hidden.includes(icon));
}
