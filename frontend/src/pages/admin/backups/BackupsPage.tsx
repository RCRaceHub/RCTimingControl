import { useState } from 'react';
import { DatabaseBackup, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useAdminBackups, useBackupNow } from '@/hooks/admin/useAdminBackups';
import { useHelpContent } from '@/context/HelpContext';
import { BackupsHelp } from '@/help/BackupsHelp';
import { getApiErrorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/dates';
import type { BackupFileDto } from '@/lib/adminApi';

const REASONS: Record<string, string> = {
  manual: 'Taken by hand',
  nightly: 'Nightly',
  'day-close': 'Race day closed',
};

/** A race day closed within this long ago still gets the "copy a backup off the laptop" prompt. */
const COPY_OFF_PROMPT_DAYS = 3;

function formatSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Database backups (#22). The app backs up when a race day is closed and every night; an admin
 * can take one now. Restoring is done with the app stopped, using the restore command.
 */
export default function BackupsPage() {
  useHelpContent(BackupsHelp);
  const { data, isLoading, isError } = useAdminBackups();
  const backupNow = useBackupNow();

  // Read once when the page opens: the prompt does not need to expire while the page is up
  const [now] = useState(() => Date.now());
  const newest = data?.backups.reduce<BackupFileDto | undefined>(
    (latest, b) => (!latest || b.createdAt > latest.createdAt ? b : latest),
    undefined,
  );
  const recentDayClose = data?.backups
    .filter(b => b.reason === 'day-close' && now - new Date(b.createdAt).getTime() < COPY_OFF_PROMPT_DAYS * 86_400_000)
    .reduce<BackupFileDto | undefined>((latest, b) => (!latest || b.createdAt > latest.createdAt ? b : latest), undefined);

  function onBackupNow() {
    backupNow.mutate(undefined, {
      onSuccess: backup => toast.success(`Backed up to ${backup.name}`),
      onError: err =>
        toast.error(getApiErrorMessage(err, 'The backup failed. Check the backup folder is there and has space.')),
    });
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Backups</h1>
          <p className="text-sm text-muted-foreground mt-1">
            The app backs up its database when you complete an event (that closes the race day) and every
            night, keeping the newest copies. Backing up is safe while racing carries on.
          </p>
        </div>
        <Button onClick={onBackupNow} disabled={backupNow.isPending}>
          {backupNow.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
          Back up now
        </Button>
      </div>

      {recentDayClose && (
        <div role="status" className="rounded-lg border border-warning/40 bg-warning/15 p-4 text-sm">
          <p className="font-semibold">Copy a backup off the laptop</p>
          <p className="text-muted-foreground mt-1">
            The race day was closed on {formatDateTime(recentDayClose.createdAt)}. The race history lives only on
            this laptop, so copy <code className="rounded bg-muted px-1 py-0.5 text-xs break-all">{recentDayClose.name}</code> from
            the backup folder to a USB stick or a synced folder before you pack up.
          </p>
        </div>
      )}

      {data && (
        <p className="text-sm">
          Last backup: <strong>{newest ? formatDateTime(newest.createdAt) : 'none yet'}</strong>
        </p>
      )}

      {data && (
        <p className="text-sm">
          Backup folder: <code className="rounded bg-muted px-1 py-0.5 text-xs break-all">{data.directory}</code>
        </p>
      )}

      {isLoading && (
        <div className="flex items-center gap-2 py-8">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          <span className="text-sm text-muted-foreground">Loading backups…</span>
        </div>
      )}

      {isError && <p className="text-sm text-destructive">Could not load backups. Check the backup folder is reachable.</p>}

      {data && data.backups.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <DatabaseBackup className="h-10 w-10 text-muted-foreground mb-4" aria-hidden="true" />
          <h2 className="text-lg font-semibold">No backups yet</h2>
          <p className="text-sm text-muted-foreground mt-1">
            The first one is taken tonight or when you complete an event (Complete Event closes the race day). You can take one now.
          </p>
        </div>
      )}

      {data && data.backups.length > 0 && (
        <ul className="divide-y rounded-lg border" aria-label="Backups">
          {data.backups.map(b => (
            <li key={b.name} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <p className="font-medium">{formatDateTime(b.createdAt)}</p>
                <p className="text-xs text-muted-foreground truncate">{b.name}</p>
              </div>
              <span className="text-xs text-muted-foreground text-right shrink-0">
                {[REASONS[b.reason] ?? b.reason, formatSize(b.sizeBytes)].join(' · ')}
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="rounded-lg border p-4 text-sm space-y-2">
        <h2 className="font-semibold">Restoring a backup</h2>
        <p className="text-muted-foreground">
          Stop the app, then run <code className="rounded bg-muted px-1 py-0.5 text-xs">java -jar app.jar restore &lt;backup file&gt;</code>.
          The current database is kept beside the restored one, and the app picks the restored data up when it
          starts again.
        </p>
      </div>
    </div>
  );
}
