/**
 * "DNF" beside a car's laps: it was still on the track when the race hit its maximum race window, so it did not take
 * the flag. It keeps its laps and its place; this is only the label.
 */
export function DnfBadge({ dnf, className }: { dnf?: boolean; className?: string }) {
  if (!dnf) return null;
  return (
    <span title="Did not finish: still on the track at the maximum race window" className={className ?? 'ml-2 text-xs font-semibold text-red-600'}>
      DNF
    </span>
  );
}
