import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { AutoRunStateDto } from '@/lib/raceControlApi';
import { formatCountdown, useCountdown } from '@/lib/useCountdown';
import type { useAutoRun } from '@/hooks/race-control/useAutoRun';

type AutoRun = ReturnType<typeof useAutoRun>;

function heldText(state: AutoRunStateDto): string {
  switch (state.heldReason) {
    case 'FINALS':
      return 'Qualifying is complete. Auto run is holding so the finals can be calculated and checked in the Finals line-up panel.';
    case 'NO_DRIVERS':
      return 'The next race has no drivers. If it is a final, calculate the finals in race control; otherwise fix or abandon it, then resume.';
    default:
      return `Auto run stopped: ${state.heldMessage ?? 'a race command was refused'}.`;
  }
}

/**
 * Automatic race progression (#338): runs the event's races one after another, with the gap between them and
 * a hold before the finals. Go starts it (and resumes it after a hold); the 30s and 10s buttons bring the
 * next start forward. Settings are saved on the event as they are changed.
 */
export function AutoRunPanel({ autoRun, canControl }: { autoRun: AutoRun; canControl: boolean }) {
  const { state } = autoRun;
  const left = useCountdown(state?.phase === 'WAITING' ? state.startAt : null, state?.serverTime);
  const [gap, setGap] = useState<string | null>(null);

  const gapValue = gap ?? String(state?.gapSeconds ?? '');
  const gapSeconds = Number(gapValue);
  const gapValid = gapValue !== '' && Number.isInteger(gapSeconds) && gapSeconds >= 0 && gapSeconds <= 3600;
  const { mutate: saveSettings } = autoRun.settings;
  const savedGap = state?.gapSeconds;
  const savedHold = state?.holdBeforeFinals;
  const savedAutoStart = state?.autoStart;

  // The settings live on the server, so a typed gap is saved a moment after the last keystroke instead of
  // waiting for a button: a browser closed mid-race then finds the gap as it was left.
  useEffect(() => {
    if (gap === null || !gapValid || savedGap === undefined || gapSeconds === savedGap) return;
    const timer = setTimeout(() => {
      saveSettings(
        { gapSeconds, holdBeforeFinals: savedHold!, autoStart: savedAutoStart! },
        { onSuccess: () => setGap(null) },
      );
    }, 800);
    return () => clearTimeout(timer);
  }, [gap, gapValid, gapSeconds, savedGap, savedHold, savedAutoStart, saveSettings]);

  if (!state) return null;
  const busy = autoRun.go.isPending || autoRun.stop.isPending;
  const saveFlag = (change: { holdBeforeFinals?: boolean; autoStart?: boolean }) =>
    saveSettings({
      gapSeconds: gapValid ? gapSeconds : state.gapSeconds,
      holdBeforeFinals: state.holdBeforeFinals,
      autoStart: state.autoStart,
      ...change,
    });

  return (
    <section className="border-b px-3 py-3 flex flex-col gap-2" aria-label="Auto run" data-testid="auto-run-panel">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Auto run</h3>

      {!state.armed && <p className="text-sm text-muted-foreground">Off. Races are started by hand.</p>}
      {state.phase === 'WAITING' && left != null && (
        <div role="status">
          <p className="text-sm font-semibold">{state.nextRaceLabel} starts in</p>
          <p className="font-mono text-4xl font-bold tabular-nums" data-testid="auto-run-countdown">
            {formatCountdown(left)}
          </p>
        </div>
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
              checked={state.holdBeforeFinals}
              onCheckedChange={(checked) => saveFlag({ holdBeforeFinals: checked === true })}
            />
            <Label htmlFor="auto-run-hold" className="text-xs">Hold after qualifying, before the finals</Label>
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="auto-run-auto-start"
              checked={state.autoStart}
              onCheckedChange={(checked) => saveFlag({ autoStart: checked === true })}
            />
            <Label htmlFor="auto-run-auto-start" className="text-xs">Switch on by itself when a race is started by hand (otherwise only Go starts it)</Label>
          </div>
        </>
      )}
    </section>
  );
}
