import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { AutoRunStateDto } from '@/lib/raceControlApi';
import { formatCountdown, useCountdown } from '@/lib/useCountdown';
import type { useAutoRun } from '@/hooks/race-control/useAutoRun';
import { MAX_RESULTS_HOLD_SECONDS, useResultsHold } from '@/hooks/race-control/useResultsHold';

type AutoRun = ReturnType<typeof useAutoRun>;

/** The longest countdown the server accepts for "start in" (AutoRunController: 0 to 600 seconds). */
const MAX_START_IN_SECONDS = 600;

/** Quick "start in" buttons, longest first; the box next to them takes any other number of seconds. */
const START_IN_PRESETS = [
  { seconds: 120, label: '2 min' },
  { seconds: 60, label: '60s' },
  { seconds: 30, label: '30s' },
  { seconds: 10, label: '10s' },
];

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
 * a hold before the finals. Go starts it (and resumes it after a hold); the start-in buttons (2 min, 60s, 30s, 10s)
 * and the seconds box bring the next start forward, never back. Settings are saved on the event as they are changed.
 */
export function AutoRunPanel({ autoRun, canControl }: { autoRun: AutoRun; canControl: boolean }) {
  const { state } = autoRun;
  const left = useCountdown(state?.phase === 'WAITING' ? state.startAt : null, state?.serverTime);
  // What has been typed or ticked and not yet shown back by the server; null means "as the server has it"
  const [gap, setGap] = useState<string | null>(null);
  const [hold, setHold] = useState<boolean | null>(null);
  const [autoStart, setAutoStart] = useState<boolean | null>(null);
  const [startIn, setStartIn] = useState('');
  const { mutate: saveSettings } = autoRun.settings;
  const [resultsHold, setResultsHold] = useResultsHold();

  const startInSeconds = Number(startIn);
  const startInValid = startIn !== '' && Number.isInteger(startInSeconds) && startInSeconds >= 0
    && startInSeconds <= MAX_START_IN_SECONDS;
  const gapValue = gap ?? String(state?.gapSeconds ?? '');
  const gapSeconds = Number(gapValue);
  const gapValid = gapValue !== '' && Number.isInteger(gapSeconds) && gapSeconds >= 0 && gapSeconds <= 3600;
  const wanted = state && gapValid
    ? { gapSeconds, holdBeforeFinals: hold ?? state.holdBeforeFinals, autoStart: autoStart ?? state.autoStart }
    : null;
  const differs = wanted != null && state != null && (wanted.gapSeconds !== state.gapSeconds
    || wanted.holdBeforeFinals !== state.holdBeforeFinals || wanted.autoStart !== state.autoStart);
  // The unsent change, so it can still be sent when the panel goes away
  const pending = useRef<typeof wanted>(null);
  pending.current = differs ? wanted : null;

  // Once the server shows what was typed or ticked, go back to showing the server's value
  useEffect(() => {
    if (!state) return;
    if (gap !== null && Number(gap) === state.gapSeconds) setGap(null);
    if (hold === state.holdBeforeFinals) setHold(null);
    if (autoStart === state.autoStart) setAutoStart(null);
  }, [state, gap, hold, autoStart]);

  // The settings live on the server, so a change is sent on its own: a tick straight away, the gap a moment
  // after the last keystroke. A browser closed mid-race then finds them as they were left.
  const gapEdited = gap !== null;
  useEffect(() => {
    if (!differs || !wanted) return;
    const timer = setTimeout(() => {
      saveSettings(wanted, {
        onError: () => { setGap(null); setHold(null); setAutoStart(null); },
      });
    }, gapEdited ? 800 : 0);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [differs, wanted?.gapSeconds, wanted?.holdBeforeFinals, wanted?.autoStart, gapEdited, saveSettings]);
  // A gap typed just before the panel goes away is still saved
  useEffect(() => () => { if (pending.current) saveSettings(pending.current); }, [saveSettings]);

  if (!state) return null;
  const busy = autoRun.go.isPending || autoRun.stop.isPending;

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
                {START_IN_PRESETS.map(({ seconds, label }) => (
                  <Button key={seconds} size="sm" variant="outline" onClick={() => autoRun.skipTo.mutate(seconds)}
                          disabled={autoRun.skipTo.isPending}>
                    {label}
                  </Button>
                ))}
                <form
                  className="flex items-center gap-1"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (startInValid) autoRun.skipTo.mutate(Number(startIn));
                  }}
                >
                  <Label htmlFor="auto-run-start-in" className="sr-only">Start in (seconds)</Label>
                  <Input
                    id="auto-run-start-in"
                    type="number"
                    min={0}
                    max={MAX_START_IN_SECONDS}
                    placeholder="secs"
                    className="h-8 w-20"
                    value={startIn}
                    onChange={(e) => setStartIn(e.target.value)}
                  />
                  <Button type="submit" size="sm" variant="outline"
                          disabled={!startInValid || autoRun.skipTo.isPending}>
                    Set
                  </Button>
                </form>
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
              onBlur={() => { if (!gapValid) setGap(null); }}
            />
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="auto-run-hold"
              checked={hold ?? state.holdBeforeFinals}
              onCheckedChange={(checked) => setHold(checked === true)}
            />
            <Label htmlFor="auto-run-hold" className="text-xs">Hold after qualifying, before the finals</Label>
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="auto-run-auto-start"
              checked={autoStart ?? state.autoStart}
              onCheckedChange={(checked) => setAutoStart(checked === true)}
            />
            <Label htmlFor="auto-run-auto-start" className="text-xs">Switch on by itself when a race is started by hand (otherwise only Go starts it)</Label>
          </div>
        </>
      )}

      <div className="flex items-center gap-2">
        <Label htmlFor="auto-run-results-hold" className="text-xs">Keep results on screen after a race (seconds)</Label>
        <Input
          id="auto-run-results-hold"
          type="number"
          min={0}
          max={MAX_RESULTS_HOLD_SECONDS}
          className="h-8 w-20"
          value={resultsHold}
          onChange={(e) => {
            const seconds = parseInt(e.target.value, 10);
            setResultsHold(Number.isFinite(seconds) ? Math.min(MAX_RESULTS_HOLD_SECONDS, Math.max(0, seconds)) : 0);
          }}
        />
      </div>
    </section>
  );
}
