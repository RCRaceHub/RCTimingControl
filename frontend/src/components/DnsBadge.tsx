/**
 * "DNS" beside a car's laps: an official recorded that it did not start. It has no laps and is listed last; this is
 * only the label.
 */
export function DnsBadge({ dns, className }: { dns?: boolean; className?: string }) {
  if (!dns) return null;
  return (
    <span title="Did not start" className={className ?? 'ml-2 text-xs font-semibold text-warning'}>
      DNS
    </span>
  );
}
