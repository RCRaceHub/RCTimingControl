import { CloudUpload, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RaceHubAddressField } from '@/components/RaceHubAddressField';
import { QueryError } from '@/components/QueryError';
import { RaceHubClassesTab } from '@/pages/admin/racehub-classes/RaceHubClassesPage';
import { useUnlinkedRaceHubClassCount } from '@/hooks/admin/useRaceHubClasses';
import { useTabParam } from '@/hooks/useTabParam';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useClearRaceHubKey, useDownloadResultsSite, useResultsExports, useRetryResultsExport, useSetRaceHubKey, useSetResultsTarget } from '@/hooks/admin/useResultsExports';
import { useHelpContent } from '@/context/HelpContext';
import { ResultsExportsHelp } from '@/help/ResultsExportsHelp';
import type { ResultsExportReason, ResultsExportRowDto, ResultsExportStatus, ResultsPublishTarget } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';
import { getApiErrorMessage } from '@/lib/errors';

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
  QUEUED: 'bg-info/15 text-info',
  FAILED: 'bg-destructive/15 text-destructive',
  SENT: 'bg-success/15 text-success',
  SUPERSEDED: 'bg-muted text-muted-foreground',
};

const TARGETS: { value: ResultsPublishTarget; label: string }[] = [
  { value: 'NONE', label: 'Nowhere' },
  { value: 'RACEHUB', label: 'RaceHub' },
  { value: 'SITE', label: 'A results site' },
  { value: 'BOTH', label: 'RaceHub and a results site' },
];

/** The results site (#359): static pages for a club's own web space, downloaded as a zip. */
function SiteDownload() {
  const download = useDownloadResultsSite();
  return (
    <div className="space-y-1">
      <Button
        type="button"
        variant="outline"
        disabled={download.isPending}
        onClick={() => download.mutate(undefined, { onError: () => toast.error('Could not build the results site.') })}
      >
        Download results site (zip)
      </Button>
      <p className="text-xs text-muted-foreground">
        Plain web pages for every event, to upload to the club&apos;s own web space. Names follow each
        competitor&apos;s public name setting, and no lap-by-lap times or penalty reasons are included.
      </p>
    </div>
  );
}

function TargetForm({ target, chosen }: { target: ResultsPublishTarget; chosen: ResultsPublishTarget | null }) {
  const setTarget = useSetResultsTarget();

  function save(next: ResultsPublishTarget | null) {
    setTarget.mutate(next, {
      onSuccess: () => toast.success('Saved.'),
      onError: () => toast.error('Could not save where results are published.'),
    });
  }

  return (
    <div className="space-y-2">
      <Label htmlFor="publish-target">Publish results to</Label>
      <div className="flex gap-2">
        <select
          id="publish-target"
          className="h-9 rounded-md border bg-background px-3 text-sm"
          value={chosen ?? ''}
          disabled={setTarget.isPending}
          onChange={e => save(e.target.value === '' ? null : (e.target.value as ResultsPublishTarget))}
        >
          <option value="">Not chosen (currently {TARGETS.find(t => t.value === target)?.label})</option>
          {TARGETS.map(t => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>
      <p className="text-xs text-muted-foreground">
        {chosen
          ? 'Set by the club.'
          : 'Not set: results go to RaceHub when a RaceHub address is set, and nowhere otherwise.'}{' '}
        Choosing a results site queues nothing yet.
      </p>
    </div>
  );
}

function detail(row: ResultsExportRowDto) {
  const parts = [REASONS[row.reason] ?? row.reason, `revision ${row.revision}`, formatDateTime(row.createdAt)];
  if (row.status === 'SENT' && row.sentAt) parts.push(`sent ${formatDateTime(row.sentAt)}`);
  if (row.status === 'FAILED') parts.push(`next try ${formatDateTime(row.nextAttemptAt)}`);
  if (row.attempts > 0 && row.status !== 'SENT') parts.push(row.attempts === 1 ? '1 attempt' : `${row.attempts} attempts`);
  return parts.join(' · ');
}

/**
 * The club's key for RaceHub. Pasting a new one takes effect at once, with no restart, so the key can be rotated:
 * make the new key in RaceHub, paste it here, then revoke the old one. The key is never shown back.
 */
function KeyForm({ source }: { source: 'SAVED' | 'SETTINGS_FILE' | 'NONE' }) {
  const setKey = useSetRaceHubKey();
  const clearKey = useClearRaceHubKey();
  const [value, setValue] = useState('');

  return (
    <form
      className="space-y-2"
      onSubmit={e => {
        e.preventDefault();
        setKey.mutate(value, {
          onSuccess: () => {
            setValue('');
            toast.success('RaceHub key saved. It is used from the next request.');
          },
          onError: err => toast.error(getApiErrorMessage(err, 'Could not save the RaceHub key.')),
        });
      }}
    >
      <Label htmlFor="racehub-key">RaceHub key</Label>
      <div className="flex gap-2">
        <Input
          id="racehub-key"
          type="password"
          autoComplete="off"
          value={value}
          onChange={e => setValue(e.target.value)}
          placeholder="Paste a new key"
        />
        <Button type="submit" disabled={setKey.isPending || value.trim() === ''}>
          Save key
        </Button>
        {source === 'SAVED' && (
          <Button
            type="button"
            variant="outline"
            disabled={clearKey.isPending}
            onClick={() =>
              clearKey.mutate(undefined, {
                onSuccess: () => toast.success('Saved key removed.'),
                onError: err => toast.error(getApiErrorMessage(err, 'Could not remove the saved key.')),
              })
            }
          >
            Clear
          </Button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        {source === 'SAVED' && 'A key saved here is in use instead of the settings file’s. '}
        {source === 'SETTINGS_FILE' && 'The key in the settings file is in use; saving one here replaces it. '}
        {source === 'NONE' && 'No key is set yet. '}
        To change keys without a restart, make the new key in RaceHub, paste it here, then revoke the old one. The key
        is never shown again, and the events already taken from RaceHub use it too.
      </p>
    </form>
  );
}

const TABS = ['sent', 'classes', 'settings'] as const;

/**
 * Results sent to RaceHub (#27). Results for an event imported from RaceHub are queued when a race
 * finishes, when a finished race is corrected, and when the race day is closed. The app sends them
 * in the background and keeps retrying while RaceHub cannot be reached.
 */
export default function ResultsExportsPage() {
  useHelpContent(ResultsExportsHelp);
  const { data, moduleOff, isLoading, isError, refetch } = useResultsExports();
  const { data: unlinked = 0 } = useUnlinkedRaceHubClassCount();
  // Picked once, from the first answer, so a refetch never moves a tab nobody chose
  const [firstTab, setFirstTab] = useState<(typeof TABS)[number] | null>(null);
  if (data && firstTab === null) setFirstTab(data.sendingEnabled && !moduleOff ? 'sent' : 'settings');
  const [requestedTab, setTab] = useTabParam(TABS, firstTab ?? 'settings');
  // The results export module (#799) can be switched off: its tab goes and a stale address falls back to the settings
  const tab = moduleOff && requestedTab === 'sent' ? 'settings' : requestedTab;
  const retry = useRetryResultsExport();
  // Once sending is set up the list of what has gone is what people come for; until then the settings are
  function onRetry(id: number) {
    retry.mutate(id, {
      onSuccess: () => toast.success('It will be sent shortly.'),
      onError: () => toast.error('Could not retry that export.'),
    });
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">RaceHub</h1>
        <p className="text-sm text-muted-foreground mt-1">
          For events whose entries came from RaceHub, the results are sent back when a race finishes, when a
          finished race is corrected, and when the event is completed. Each send carries the whole event, so a
          newer one replaces any that have not gone yet. Racing is never held up by sending.
        </p>
      </div>

        {isLoading && (
          <div className="flex items-center gap-2 py-8">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            <span className="text-sm text-muted-foreground">Loading results sent to RaceHub…</span>
          </div>
        )}

        {isError && <QueryError message="Could not load the results sent to RaceHub." onRetry={refetch} />}


        {data?.sendingEnabled && !moduleOff && (
          <p className="text-sm">
            Sending to <code className="rounded bg-muted px-1 py-0.5 text-xs break-all">{data.resultsUrl}</code>
          </p>
        )}

        {data && !data.sendingEnabled && !moduleOff && (
          <div className="rounded-lg border border-warning/50 bg-warning/10 p-4 text-sm">
            <p className="font-medium">
              {data.resultsUrl ? 'No RaceHub key is set' : 'No RaceHub address is set'}, so results wait here.
            </p>
            <p className="text-muted-foreground mt-1">
              {!data.resultsUrl && 'Enter the address on the Connection tab. '}
              {data.missingSettings.includes('rctiming.racehub.token') && (
                <>
                  Paste the key on the Connection tab, or set <code className="rounded bg-muted px-1 py-0.5 text-xs">rctiming.racehub.token</code> in{' '}
                  <code className="rounded bg-muted px-1 py-0.5 text-xs">application.properties</code> in the data
                  folder and restart the app.{' '}
                </>
              )}
              You can still download an event&apos;s results from its page.
            </p>
          </div>
        )}

      {/* Tabs mount once the first answer is in, so the starting tab is picked then and a refresh never moves it:
          the list once sending is set up, otherwise the settings */}
      {data && (
        <Tabs value={tab} onValueChange={setTab} className="space-y-6">
          <TabsList>
            {!moduleOff && <TabsTrigger value="sent">Sent results</TabsTrigger>}
            <TabsTrigger value="classes">
              Classes and formats
              {unlinked > 0 && <Badge variant="outline" className="ml-2">{unlinked} unlinked</Badge>}
            </TabsTrigger>
            <TabsTrigger value="settings">Connection</TabsTrigger>
          </TabsList>

          <TabsContent value="classes"><RaceHubClassesTab /></TabsContent>

          {/* The sent and connection tabs stay mounted (hidden when not shown) so a half-typed address or key is not lost on a switch */}
          {!moduleOff && (
          <TabsContent value="sent" forceMount className="space-y-6 data-[state=inactive]:hidden">
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
          </TabsContent>
          )}

          <TabsContent value="settings" forceMount className="space-y-6 data-[state=inactive]:hidden">
            {data && !moduleOff && <TargetForm key={data.chosenTarget ?? 'default'} target={data.publishTarget} chosen={data.chosenTarget} />}

            {data && !moduleOff && (data.publishTarget === 'SITE' || data.publishTarget === 'BOTH') && <SiteDownload />}

            {data && <RaceHubAddressField id="racehub-address" address={data.raceHubUrl} source={data.addressSource} />}

            {data && <KeyForm source={data.keySource} />}
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}
