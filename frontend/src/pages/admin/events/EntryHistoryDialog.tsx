import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useEntryHistory } from '@/hooks/admin/useAdminEntries';
import type { AdminEntryDto } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';
import { QueryError } from '@/components/QueryError';

/** What has happened to one entry, oldest first: added, checked in, transponder swapped, withdrawn (#140). */
export default function EntryHistoryDialog({
  entry,
  onClose,
}: {
  entry: AdminEntryDto;
  onClose: () => void;
}) {
  const { data, isPending, isError, refetch } = useEntryHistory(entry.id);

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>History</DialogTitle>
          <DialogDescription>{entry.displayName ?? 'Unknown driver'}&apos;s entry</DialogDescription>
        </DialogHeader>
        {isPending && (
          <p role="status" className="text-sm text-muted-foreground">Loading…</p>
        )}
        {isError && (
          <QueryError message="Could not load the history." onRetry={refetch} />
        )}
        {data && data.length === 0 && (
          <p className="text-sm text-muted-foreground">
            No officials&apos; actions are recorded for this entry. Changes made by importing an entry file are
            not listed here.
          </p>
        )}
        {data && data.length > 0 && (
          <ul className="divide-y rounded-md border text-sm">
            {data.map((item, i) => (
              <li key={`${item.at}-${i}`} className="space-y-0.5 px-3 py-2">
                <p>{item.summary}</p>
                {item.reason && <p className="text-muted-foreground">Reason: {item.reason}</p>}
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(item.at)}
                  {item.actor && ` by ${item.actor}`}
                </p>
              </li>
            ))}
          </ul>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
