import { useEffect, useState } from 'react';

/**
 * The current time in UTC milliseconds, this device's clock plus {@code offsetMs} (the server's clock minus this
 * browser's, 0 where they agree), kept moving about ten times a second while {@code active}.
 */
export function useServerNow(offsetMs: number, active = true): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(timer);
  }, [active]);
  return now + offsetMs;
}
