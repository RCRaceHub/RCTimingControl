import { useState } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';

import {
  useDeleteEntryFeed,
  useEntryFeed,
  useFetchEntryFeed,
  useSaveEntryFeed,
} from '@/hooks/admin/useEntryFeed';
import type { EntryFeedDto, EventClassDto, SaveEntryFeedRequest } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';
import RaceHubImportDialog from './RaceHubImportDialog';
import { useConfirm } from '@/components/ConfirmDialog';
import { formatDateTime } from '@/lib/dates';

interface EntryFeedSectionProps {
  eventId: number;
  classes: EventClassDto[];
}

const STATUS_TEXT: Record<NonNullable<EntryFeedDto['lastStatus']>, string> = {
  APPLIED: 'Imported',
  UNCHANGED: 'No changes',
  WAITING: 'Waiting for you to review it',
  AUTH_FAILED: 'Token refused',
  FAILED: 'Failed',
};

/**
 * Pull an event's entries from a URL that serves Entry Export v1 (#42): the URL and its token, whether to fetch
 * automatically, the latest outcome, and a fetched file waiting to be reviewed.
 */
export default function EntryFeedSection({ eventId, classes }: EntryFeedSectionProps) {
  const { data: feed, isLoading } = useEntryFeed(eventId);
  const saveFeed = useSaveEntryFeed(eventId);
  const deleteFeed = useDeleteEntryFeed(eventId);
  const fetchFeed = useFetchEntryFeed(eventId);
  const { confirm, dialog: confirmDialog } = useConfirm();

  const [url, setUrl] = useState('');
  const [token, setToken] = useState('');
  const [autoFetch, setAutoFetch] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);

  // Fill the form from the saved feed whenever it changes (adjusting state while rendering rather than in an
  // effect). The token is never sent back, so its field stays empty and leaving it empty keeps the saved one.
  const savedKey = feed ? `${feed.url}|${feed.autoFetch}` : '';
  const [formKey, setFormKey] = useState<string | null>(null);
  if (!isLoading && formKey !== savedKey) {
    setFormKey(savedKey);
    setUrl(feed?.url ?? '');
    setAutoFetch(feed?.autoFetch ?? false);
    setToken('');
  }

  if (isLoading) {
    return null;
  }

  const busy = saveFeed.isPending || deleteFeed.isPending || fetchFeed.isPending;

  function handleSave() {
    const request: SaveEntryFeedRequest = { url: url.trim(), autoFetch };
    if (token.trim()) {
      request.token = token.trim();
    }
    saveFeed.mutate(request, {
      onSuccess: () => {
        setToken('');
        toast.success('Entry feed saved');
      },
      onError: error => toast.error(getApiErrorMessage(error, 'Could not save the entry feed')),
    });
  }

  function handleRemoveToken() {
    saveFeed.mutate(
      { url: feed?.url ?? url.trim(), token: '', autoFetch: feed?.autoFetch ?? autoFetch },
      {
        onSuccess: () => toast.success('Token removed'),
        onError: error => toast.error(getApiErrorMessage(error, 'Could not remove the token')),
      }
    );
  }

  async function handleDelete() {
    const confirmed = await confirm({
      title: 'Remove the entry feed?',
      description: 'Its URL, token and any fetched file waiting for review are forgotten. Entries already imported stay.',
      confirmLabel: 'Remove',
      destructive: true,
    });
    if (!confirmed) return;
    deleteFeed.mutate(undefined, {
      onSuccess: () => toast.success('Entry feed removed'),
      onError: error => toast.error(getApiErrorMessage(error, 'Could not remove the entry feed')),
    });
  }

  function handleFetch() {
    fetchFeed.mutate(undefined, {
      onSuccess: result => {
        if (result.lastStatus === 'WAITING') {
          toast.success('Fetched. Review the file to import it.');
        } else if (result.lastStatus === 'UNCHANGED') {
          toast.success('Fetched. Nothing has changed since the last import.');
        } else {
          toast.error(result.lastMessage ?? 'The fetch failed');
        }
      },
      onError: error => toast.error(getApiErrorMessage(error, 'Could not fetch the entry feed')),
    });
  }

  return (
    <section className="space-y-3 rounded-lg border px-4 py-3" aria-labelledby="entry-feed-heading">
      <div>
        <h3 id="entry-feed-heading" className="text-sm font-medium">
          Entry feed
        </h3>
        <p className="text-sm text-muted-foreground">
          Fetch this event&apos;s entries from a booking system that publishes an Entry Export file at a URL.
        </p>
      </div>

      {feed?.waiting && (
        <div
          role="status"
          className="flex flex-col sm:flex-row sm:items-center gap-2 rounded-md border border-warning/50 bg-warning/10 px-3 py-2 text-sm"
        >
          <p className="flex-1">
            {feed.lastStatus === 'WAITING' && feed.lastMessage ? (
              feed.lastMessage
            ) : (
              <>
                A fetched file
                {feed.waitingRevision != null && <> (revision {feed.waitingRevision})</>} is waiting to be reviewed.
              </>
            )}
          </p>
          <Button size="sm" onClick={() => setReviewOpen(true)}>
            Review and import
          </Button>
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor="entry-feed-url">URL</Label>
          <Input
            id="entry-feed-url"
            type="url"
            placeholder="https://booking.example.org/events/123/entries"
            value={url}
            onChange={e => setUrl(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="entry-feed-token">Access token</Label>
          <Input
            id="entry-feed-token"
            type="password"
            autoComplete="off"
            placeholder={
              feed?.tokenSaved
                ? feed.tokenHint
                  ? `Saved, ending ${feed.tokenHint}. Leave empty to keep it.`
                  : 'Saved. Leave empty to keep it.'
                : 'None'
            }
            value={token}
            onChange={e => setToken(e.target.value)}
          />
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Switch id="entry-feed-auto" checked={autoFetch} onCheckedChange={setAutoFetch} />
        <Label htmlFor="entry-feed-auto" className="font-normal">
          Fetch every few minutes and import changes that apply cleanly
        </Label>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" onClick={handleSave} disabled={busy || !url.trim()}>
          {saveFeed.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Save
        </Button>
        {feed && (
          <>
            <Button size="sm" variant="outline" onClick={handleFetch} disabled={busy}>
              {fetchFeed.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Fetch now
            </Button>
            {feed.tokenSaved && (
              <Button size="sm" variant="ghost" onClick={handleRemoveToken} disabled={busy}>
                Remove token
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={handleDelete} disabled={busy}>
              Remove feed
            </Button>
          </>
        )}
      </div>

      {feed?.lastFetchAt && (
        <p className="text-sm text-muted-foreground" data-testid="entry-feed-status">
          Last fetched {formatDateTime(feed.lastFetchAt)}
          {feed.lastStatus && <>: {STATUS_TEXT[feed.lastStatus]}</>}
          {feed.lastStatus !== 'WAITING' && feed.lastMessage && <>. {feed.lastMessage}</>}
          {feed.appliedRevision != null && <> (last imported revision {feed.appliedRevision})</>}
        </p>
      )}

      {feed?.waiting && (
        <RaceHubImportDialog
          eventId={eventId}
          classes={classes}
          open={reviewOpen}
          onOpenChange={setReviewOpen}
          feed
        />
      )}
      {confirmDialog}
    </section>
  );
}
