/** A chequered flag after a driver's name: the driver has taken the flag and is finished. */
export function FinishedFlag({ className }: { className?: string }) {
  return (
    <span role="img" aria-label="Finished" title="Finished" className={className ?? 'ml-2'}>
      🏁
    </span>
  );
}
