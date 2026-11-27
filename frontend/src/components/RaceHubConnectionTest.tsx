import { CheckCircle2, CircleDashed, Loader2, MinusCircle, XCircle } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { useTestRaceHubConnection } from '@/hooks/admin/useRaceHubConnection';
import type { RaceHubConnectionCheckDto } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';
import { getApiErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/utils';

function StateIcon({ state }: { state: RaceHubConnectionCheckDto['state'] }) {
  if (state === 'PASS') return <CheckCircle2 className="h-4 w-4 text-success shrink-0" aria-hidden="true" />;
  if (state === 'FAIL') return <XCircle className="h-4 w-4 text-destructive shrink-0" aria-hidden="true" />;
  if (state === 'UNKNOWN') return <CircleDashed className="h-4 w-4 text-warning shrink-0" aria-hidden="true" />;
  return <MinusCircle className="h-4 w-4 text-muted-foreground shrink-0" aria-hidden="true" />;
}

const STATE_WORD: Record<RaceHubConnectionCheckDto['state'], string> = {
  PASS: 'Passed',
  FAIL: 'Failed',
  UNKNOWN: 'Unclear',
  SKIPPED: 'Not tried',
};

/**
 * "Test connection" for RaceHub, beside the address and key it checks: whether the address is set, the host
 * answers, the key is accepted and may read entries, and may send results. Each line says what to fix. It uses
 * what is saved, so save the address or key first. It changes nothing in RaceHub (the results check sends an
 * empty body, which RaceHub refuses after checking the key's ability).
 */
export function RaceHubConnectionTest() {
  const test = useTestRaceHubConnection();
  const result = test.data;

  return (
    <section className="space-y-2" aria-labelledby="racehub-test-heading">
      <div className="flex items-center gap-3">
        <h3 id="racehub-test-heading" className="text-sm font-medium">
          Check the connection
        </h3>
        <Button type="button" variant="outline" size="sm" disabled={test.isPending} onClick={() => test.mutate()}>
          {test.isPending ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Testing…
            </>
          ) : (
            'Test connection'
          )}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Tests the address and key saved above: that RaceHub answers, accepts the key, and lets it read entries and send
        results. Nothing is changed in RaceHub.
      </p>
      {test.isError && (
        <p role="alert" className="text-sm text-destructive">
          {getApiErrorMessage(test.error, 'The test could not be run.')}
        </p>
      )}
      {result && (
        <div className="rounded-md border p-3 space-y-2" aria-live="polite">
          <p className={cn('text-sm font-medium', result.ok ? 'text-success' : 'text-destructive')}>{result.summary}</p>
          <ul className="space-y-1.5">
            {result.checks.map(check => (
              <li key={check.id} className="flex items-start gap-2 text-sm">
                <StateIcon state={check.state} />
                <span>
                  <span className="font-medium">{check.label}</span>
                  <span className="sr-only"> ({STATE_WORD[check.state]})</span>
                  <span className="block text-xs text-muted-foreground">{check.detail}</span>
                </span>
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">Tested {formatDateTime(result.checkedAt)}.</p>
        </div>
      )}
    </section>
  );
}
