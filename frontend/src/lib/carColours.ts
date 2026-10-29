/**
 * The colours to draw for a car (#685): the ones an official set on its entry, else the colour of its class,
 * else none. Both arguments are optional because a car with no media, or a class with no colour, is normal.
 */
export function carColoursFor(entryColours: string[] | null | undefined, classColour: string | null | undefined): string[] {
  if (entryColours && entryColours.length > 0) return entryColours;
  return classColour ? [classColour] : [];
}

/** A CSS background for these colours: one flat colour, or equal hard-edged stripes for several. */
export function carColoursBackground(colours: string[]): string {
  if (colours.length === 1) return colours[0];
  const step = 100 / colours.length;
  const stops = colours.map((c, i) => `${c} ${i * step}% ${(i + 1) * step}%`);
  return `linear-gradient(90deg, ${stops.join(', ')})`;
}
