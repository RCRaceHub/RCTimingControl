import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { AutoRunStateDto } from '@/lib/raceControlApi';
import type { useAutoRun } from '@/hooks/race-control/useAutoRun';

type AutoRun = ReturnType<typeof useAutoRun>;

/** Whole seconds until {@code startAt}, ticking once a second; null when nothing is scheduled. */
function useSecondsUntil(startAt: string | null | undefined): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!startAt) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [startAt]);
  return startAt ? Math.max(0, Math.ceil((new Date(startAt).getTime() - now) / 1000)) : null;
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

function heldText(state: AutoRunStateDto): string {
  switch (state.heldReason) {
    case 'FINALS':
      return 'Qualifying is complete. Auto run is holding so the finals can be checked or seeded again.';
    case 'NO_DRIVERS':
      return 'The next race has no drivers. Fix or abandon it, then resume.';
    default:
      return `Auto run stopped: ${state.heldMessage ?? 'a race command was refused'}.`;
  }
}

/**
 * Automatic race progression (#338): runs the event's races one after another, with the gap between them and
 * a hold before the finals. Go starts it (and resumes it after a hold); the 30s and 10s buttons bring the
 * next start forward.
 */
export function AutoRunPanel({ autoRun, canControl }: { autoRun: AutoRun; canControl: boolean }) {
  const { state } = autoRun;
  const left = useSecondsUntil(state?.phase === 'WAITING' ? state.startAt : null);
  const [gap, setGap] = useState<string>('');
  const [hold, setHold] = useState<boolean | null>(null);

  if (!state) return null;
  const gapValue = gap !== '' ? gap : String(state.gapSeconds);
  const holdValue = hold ?? state.holdBeforeFinals;
  const busy = autoRun.go.isPending || autoRun.stop.isPending;
  const gapSeconds = Number(gapValue);
  const gapValid = Number.isInteger(gapSeconds) && gapSeconds >= 0 && gapSeconds <= 3600;
  const settingsChanged = gapSeconds !== state.gapSeconds || holdValue !== state.holdBeforeFinals;

  return (
    <section className="border-b px-3 py-3 flex flex-col gap-2" aria-label="Auto run" data-testid="auto-run-panel">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Auto run</h3>

      {!state.armed && <p className="text-sm text-muted-foreground">Off. Races are started by hand.</p>}
      {state.phase === 'WAITING' && left != null && (
        <p className="text-sm" role="status">
          <span className="font-semibold">{state.nextRaceLabel}</span> starts in{' '}
          <span className="font-mono font-semibold" data-testid="auto-run-countdown">{formatCountdown(left)}</span>
        </p>
      )}
      {state.phase === 'RACING' && (
        <p className="text-sm text-muted-foreground" role="status">
          Waiting for {state.nextRaceLabel ?? 'the race'} to finish.
        </p>
      )}
      {state.phase === 'HELD' && (
        <p className="text-sm text-destructive" role="status" data-testid="auto-run-held">
          {heldText(state)} {state.nextRaceLabel && <>Next: {state.nextRaceLabel}.</>}
        </p>
      )}

      {canControl && (
        <>
          <div className="flex flex-wrap gap-2">
            {(!state.armed || state.phase === 'HELD') && (
              <Button size="sm" onClick={() => autoRun.go.mutate()} disabled={busy}>
                {state.phase === 'HELD' ? 'Resume' : 'Go'}
              </Button>
            )}
            {state.armed && (
              <Button size="sm" variant="outline" onClick={() => autoRun.stop.mutate()} disabled={busy}>
                Stop
              </Button>
            )}
            {state.phase === 'WAITING' && (
              <>
                <Button size="sm" variant="outline" onClick={() => autoRun.skipTo.mutate(30)}
                        disabled={autoRun.skipTo.isPending}>
                  30s
                </Button>
                <Button size="sm" variant="outline" onClick={() => autoRun.skipTo.mutate(10)}
                        disabled={autoRun.skipTo.isPending}>
                  10s
                </Button>
              </>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Label htmlFor="auto-run-gap" className="text-xs">Gap between races (seconds)</Label>
            <Input
              id="auto-run-gap"
              type="number"
              min={0}
              max={3600}
              className="h-8 w-20"
              value={gapValue}
              onChange={(e) => setGap(e.target.value)}
            />
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="auto-run-hold"
              checked={holdValue}
              onCheckedChange={(checked) => setHold(checked === true)}
            />
            <Label htmlFor="auto-run-hold" className="text-xs">Hold after qualifying, before the finals</Label>
          </div>
          {settingsChanged && (
            <Button
              size="sm"
              variant="outline"
              className="self-start"
              disabled={!gapValid || autoRun.settings.isPending}
              onClick={() =>
                autoRun.settings.mutate(
                  { gapSeconds, holdBeforeFinals: holdValue },
                  { onSuccess: () => { setGap(''); setHold(null); } },
                )
              }
            >
              Save settings
            </Button>
          )}
        </>
      )}
    </section>
  );
}
