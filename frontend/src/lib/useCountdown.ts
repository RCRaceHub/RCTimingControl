import { useEffect, useState } from 'react';

/**
 * Whole seconds until {@code startAt}, ticking once a second; null when nothing is scheduled. When the server
 * sent its own clock ({@code serverTime}, taken when the answer was built) the count follows the server's
 * clock, so a laptop or tablet whose own clock is a little out still agrees with the race.
 */
export function useCountdown(startAt: string | null | undefined, serverTime?: string | null): number | null {
  // The server's idea of now: this browser's clock moved by the gap measured when the answer arrived. Null until
  // the first tick, so a skewed clock never flashes a wrong count.
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    if (!startAt) return;
    const offsetMs = serverTime ? new Date(serverTime).getTime() - Date.now() : 0;
    const tick = () => setNow(Date.now() + offsetMs);
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [startAt, serverTime]);
  return startAt && now !== null ? Math.max(0, Math.ceil((new Date(startAt).getTime() - now) / 1000)) : null;
}

/** A countdown as m:ss. */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
