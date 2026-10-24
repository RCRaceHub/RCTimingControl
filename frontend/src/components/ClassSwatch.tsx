/**
 * A small coloured dot marking a racing class, in the colour the club picked for it (#482). Shows nothing when the
 * class has no colour, so a club that picks none sees no change.
 */
export function ClassSwatch({ colour, className = '' }: { colour?: string | null; className?: string }) {
  if (!colour) return null;
  return (
    <span
      aria-hidden="true"
      data-testid="class-swatch"
      className={`inline-block h-[0.7em] w-[0.7em] shrink-0 rounded-full align-baseline ${className}`}
      style={{ backgroundColor: colour }}
    />
  );
}
