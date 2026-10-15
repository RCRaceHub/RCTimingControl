import { useEffect, useState } from 'react';
import type { RaceClockDto } from '@/lib/boardsApi';
import { fmtClock } from '@/pages/boards/boardFormat';

/**
 * The race clock for race control: the time left, or the time so far for a race with no set length. The app
 * sends the clock when the race starts or resumes; between those it counts here, and holds still while the race
 * is stopped. {@code readAt} is when the clock was read.
 */
export function RaceClockDisplay({ clock, readAt }: { clock: RaceClockDto | undefined; readAt: number }) {
  const [now, setNow] = useState(() => Date.now());
  const running = clock?.running === true;
  useEffect(() => {
    if (!running) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [running]);

  if (!clock) return null;
  const sinceRead = running ? Math.max(0, now - readAt) : 0;
  const timed = clock.remainingMs != null;
  const text = timed ? fmtClock(clock.remainingMs! - sinceRead) : fmtClock(clock.elapsedMs + sinceRead);

  return (
    <div className="flex items-baseline gap-2" role="timer" aria-label={timed ? 'Time left' : 'Race time'}>
      <span className="text-sm text-muted-foreground">{timed ? 'Time left' : 'Race time'}</span>
      <span className="text-2xl font-bold tabular-nums">{text}</span>
      {!running && <span className="text-xs font-semibold uppercase text-muted-foreground">stopped</span>}
    </div>
  );
}
