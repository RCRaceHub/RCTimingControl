import { Button } from '@/components/ui/button';
import { failedIds, type SendOutcome } from './sendOutcome';

/**
 * Shows how the last send went: a plain "reached N screens" line, or, for a step with failures, each screen that failed
 * with the screen's own reason (an unreachable screen never holds up the others), and a button to send again to just those.
 */
export function SendResults({ outcome, busy, onRetry, onDismiss }: {
  outcome: SendOutcome;
  busy: boolean;
  onRetry: () => void;
  onDismiss: () => void;
}) {
  const failed = failedIds(outcome);
  return (
    <div className="rounded-lg border p-3 text-sm space-y-1" role="status" aria-label="Result of the last send">
      <div className="font-medium">{outcome.title}</div>
      {outcome.steps.map(step => {
        const bad = step.results.filter(r => !r.ok);
        return (
          <div key={step.label}>
            <span className={bad.length === 0 ? 'text-success' : 'text-destructive'}>
              {step.label}: {step.results.length - bad.length} of {step.results.length} screen(s) done
            </span>
            {bad.length > 0 && (
              <ul className="ml-4 list-disc text-xs text-destructive">
                {bad.map(r => <li key={r.id}>{r.name}: {r.error ?? 'failed'}</li>)}
              </ul>
            )}
          </div>
        );
      })}
      <div className="flex gap-2 pt-1">
        {failed.length > 0 && (
          <Button size="sm" variant="outline" disabled={busy} onClick={onRetry}>
            Send again to the {failed.length} that failed
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={onDismiss}>Dismiss</Button>
      </div>
    </div>
  );
}
