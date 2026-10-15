/**
 * The imported file's transponder numbers where they differ from a swap made on the day (#50). The swap is kept;
 * this only flags the difference.
 */
export function ImportedTransponderDifference({
  primary,
  secondary,
}: {
  primary: string | null;
  secondary: string | null;
}) {
  if (!primary && !secondary) return null;
  const numbers = [primary && `transponder ${primary}`, secondary && `secondary ${secondary}`].filter(Boolean);
  return (
    <span className="block text-xs text-amber-700 dark:text-amber-400" data-testid="imported-transponder-difference">
      Booking has {numbers.join(' and ')}. Keeping the number swapped on the day.
    </span>
  );
}
