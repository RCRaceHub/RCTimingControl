import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getForcedClose, type RaceClockDto } from '@/lib/boardsApi';
import { fmtClock } from '@/pages/boards/boardFormat';

/**
 * Once the race time is up, the time left before the race is closed by force (the track's max last lap, or the
 * format's race window). The app answers every few seconds because a lap that counts puts the wait back; between
 * answers it counts down here. Shown only while the race is running.
 */
function ForcedCloseCountdown({ raceId }: { raceId: number }) {
  const { data, dataUpdatedAt } = useQuery({
    queryKey: ['forced-close', raceId],
    queryFn: () => getForcedClose(raceId),
    refetchInterval: 3000,
    gcTime: 0,
  });
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(timer);
  }, []);
  if (data?.closesInMs == null) return null;
  const left = Math.max(0, data.closesInMs - Math.max(0, now - dataUpdatedAt));
  return (
    <div className="flex items-baseline gap-2" role="timer" aria-label="Time until the race is closed">
      <span className="text-sm text-destructive">Closes in</span>
      <span className="text-2xl font-bold tabular-nums text-destructive">{fmtClock(left)}</span>
    </div>
  );
}

/**
 * The race clock for race control: the time left, or the time so far for a race with no set length. The app
 * sends the clock when the race starts or resumes; between those it counts here, and holds still while the race
 * is stopped (or, for a rolling start, until the first car crosses). {@code readAt} is when the clock was read.
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

  const overrun = timed && running && clock.remainingMs! - sinceRead <= 0;

  return (
    <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1">
      <div className="flex items-baseline gap-2" role="timer" aria-label={timed ? 'Time left' : 'Race time'}>
        <span className="text-sm text-muted-foreground">{timed ? 'Time left' : 'Race time'}</span>
        <span className="text-2xl font-bold tabular-nums">{text}</span>
        {!running && (
          <span className="text-xs font-semibold uppercase text-muted-foreground">
            {/* A rolling start is running but its clock waits for the first car */}
            {clock.status === 'RUNNING' ? 'waiting for first car' : 'stopped'}
          </span>
        )}
      </div>
      {overrun && <ForcedCloseCountdown raceId={clock.raceId} />}
    </div>
  );
}
