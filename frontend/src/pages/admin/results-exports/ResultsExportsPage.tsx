import axios from 'axios';
import { CloudUpload, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useResultsExports, useRetryResultsExport, useSetResultsAddress } from '@/hooks/admin/useResultsExports';
import { useHelpContent } from '@/context/HelpContext';
import { ResultsExportsHelp } from '@/help/ResultsExportsHelp';
import type { ResultsExportReason, ResultsExportRowDto, ResultsExportStatus } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';

const REASONS: Record<ResultsExportReason, string> = {
  RACE_FINISHED: 'Race finished',
  CORRECTION: 'Correction',
  DAY_CLOSE: 'Race day closed',
};

const STATUS_LABEL: Record<ResultsExportStatus, string> = {
  QUEUED: 'Waiting to send',
  FAILED: 'Failed, will retry',
  SENT: 'Sent',
  SUPERSEDED: 'Replaced by a newer export',
};

const STATUS_COLOR: Record<ResultsExportStatus, string> = {
  QUEUED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  SENT: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  SUPERSEDED: 'bg-muted text-muted-foreground',
};

function detail(row: ResultsExportRowDto) {
  const parts = [REASONS[row.reason] ?? row.reason, `revision ${row.revision}`, formatDateTime(row.createdAt)];
  if (row.status === 'SENT' && row.sentAt) parts.push(`sent ${formatDateTime(row.sentAt)}`);
  if (row.status === 'FAILED') parts.push(`next try ${formatDateTime(row.nextAttemptAt)}`);
  if (row.attempts > 0 && row.status !== 'SENT') parts.push(row.attempts === 1 ? '1 attempt' : `${row.attempts} attempts`);
  return parts.join(' · ');
}

function AddressForm({ savedUrl, inUse }: { savedUrl: string | null; inUse: string | null }) {
  const setAddress = useSetResultsAddress();
  const [value, setValue] = useState(savedUrl ?? '');

  function save(address: string) {
    setAddress.mutate(address, {
      onSuccess: () => toast.success(address.trim() ? 'RaceHub address saved.' : 'RaceHub address cleared.'),
      onError: err => {
        const message = axios.isAxiosError(err) ? err.response?.data?.detail : undefined;
        toast.error(typeof message === 'string' ? message : 'Could not save the RaceHub address.');
      },
    });
  }

  return (
    <form
      className="space-y-2"
      onSubmit={e => {
        e.preventDefault();
        save(value);
      }}
    >
      <Label htmlFor="racehub-address">RaceHub address</Label>
      <div className="flex gap-2">
        <Input
          id="racehub-address"
          type="url"
          value={value}
          onChange={e => setValue(e.target.value)}
          placeholder="https://racehub.example/api/results"
        />
        <Button type="submit" disabled={setAddress.isPending || value.trim() === (savedUrl ?? '')}>
          Save
        </Button>
        {savedUrl && (
          <Button type="button" variant="outline" disabled={setAddress.isPending} onClick={() => save('')}>
            Clear
          </Button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        Where results are sent. It must start with https://, since the club&apos;s key goes with every request.
        {!savedUrl && inUse && ' This is using the address in the settings file; saving one here replaces it.'}
        {!savedUrl && !inUse && ' Left empty, the address in the settings file is used, if there is one.'}
      </p>
    </form>
  );
}

/**
 * Results sent to RaceHub (#27). Results for an event imported from RaceHub are queued when a race
 * finishes, when a finished race is corrected, and when the race day is closed. The app sends them
 * in the background and keeps retrying while RaceHub cannot be reached.
 */
export default function ResultsExportsPage() {
  useHelpContent(ResultsExportsHelp);
  const { data, isLoading, isError } = useResultsExports();
  const retry = useRetryResultsExport();

  function onRetry(id: number) {
    retry.mutate(id, {
      onSuccess: () => toast.success('It will be sent shortly.'),
      onError: () => toast.error('Could not retry that export.'),
    });
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Results to RaceHub</h1>
        <p className="text-sm text-muted-foreground mt-1">
          For events whose entries came from RaceHub, the results are sent back when a race finishes, when a
          finished race is corrected, and when the event is completed. Each send carries the whole event, so a
          newer one replaces any that have not gone yet. Racing is never held up by sending.
        </p>
      </div>

      {data && <AddressForm key={data.savedUrl ?? ''} savedUrl={data.savedUrl} inUse={data.resultsUrl} />}

      {data?.sendingEnabled && (
        <p className="text-sm">
          Sending to <code className="rounded bg-muted px-1 py-0.5 text-xs break-all">{data.resultsUrl}</code>
        </p>
      )}

      {data && !data.sendingEnabled && (
        <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm dark:border-amber-800 dark:bg-amber-950/30">
          <p className="font-medium">
            {data.resultsUrl ? 'No RaceHub key is set' : 'No RaceHub address is set'}, so results wait here.
          </p>
          <p className="text-muted-foreground mt-1">
            {!data.resultsUrl && 'Enter the address above. '}
            {data.missingSettings.includes('rctiming.racehub.token') && (
              <>
                Set <code className="rounded bg-muted px-1 py-0.5 text-xs">rctiming.racehub.token</code> in{' '}
                <code className="rounded bg-muted px-1 py-0.5 text-xs">application.properties</code> in the data
                folder, then restart the app.{' '}
              </>
            )}
            You can still download an event&apos;s results from its page.
          </p>
        </div>
      )}

      {isLoading && (
        <div className="flex items-center gap-2 py-8">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          <span className="text-sm text-muted-foreground">Loading results sent to RaceHub…</span>
        </div>
      )}

      {isError && <p className="text-sm text-destructive">Could not load the results sent to RaceHub.</p>}

      {data && data.exports.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <CloudUpload className="h-10 w-10 text-muted-foreground mb-4" aria-hidden="true" />
          <h2 className="text-lg font-semibold">Nothing sent yet</h2>
          <p className="text-sm text-muted-foreground mt-1">
            Results appear here once a race finishes in an event imported from RaceHub.
          </p>
        </div>
      )}

      {data && data.exports.length > 0 && (
        <ul className="divide-y rounded-lg border" aria-label="Results exports">
          {data.exports.map(row => (
            <li key={row.id} className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-4 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium truncate">{row.eventName}</p>
                <p className="text-xs text-muted-foreground">{detail(row)}</p>
                {row.lastError && row.status !== 'SENT' && (
                  <p className="text-xs text-destructive mt-1 break-words">{row.lastError}</p>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <Badge className={STATUS_COLOR[row.status]}>{STATUS_LABEL[row.status] ?? row.status}</Badge>
                {(row.status === 'FAILED' || row.status === 'QUEUED') && data.sendingEnabled && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => onRetry(row.id)}
                    disabled={retry.isPending}
                    aria-label={`Send ${row.eventName} revision ${row.revision} now`}
                  >
                    Send now
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
