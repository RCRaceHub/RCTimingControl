import { useEffect, useMemo, useState } from 'react';

/**
 * Whole seconds until {@code startAt}, ticking once a second; null when nothing is scheduled. When the server
 * sent its own clock ({@code serverTime}, taken when the answer was built) the count follows the server's
 * clock, so a laptop or tablet whose own clock is a little out still agrees with the race.
 */
export function useCountdown(startAt: string | null | undefined, serverTime?: string | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!startAt) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [startAt]);
  // The gap between the server's clock and this one, measured when the answer arrived.
  const offsetMs = useMemo(
    () => (serverTime ? new Date(serverTime).getTime() - Date.now() : 0),
    [serverTime],
  );
  return startAt ? Math.max(0, Math.ceil((new Date(startAt).getTime() - (now + offsetMs)) / 1000)) : null;
}

/** A countdown as m:ss. */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
