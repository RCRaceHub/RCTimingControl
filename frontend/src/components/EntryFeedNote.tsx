import { useState } from 'react';
import { X } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { EntryFeedSummaryDto } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';

/** The part of a feed the note needs; both the full feed and its short form have it. */
type FeedLike = Pick<EntryFeedSummaryDto, 'autoFetch' | 'lastFetchAt' | 'lastStatus' | 'lastMessage' | 'appliedRevision' | 'waiting'>;

const seenKey = (eventId: number) => `rctc.entryFeedSeen.${eventId}`;

function readSeen(eventId: number): string | null {
  try {
    return window.localStorage.getItem(seenKey(eventId));
  } catch {
    return null;
  }
}

/**
 * What an event's entry feed last did, in a line: a fetched file waiting for a decision, entries updated (with what
 * changed, until dismissed), a refused key, or an event RaceHub no longer serves. Renders nothing when there is
 * nothing to say. "Dismissed" is remembered in this browser only, per event and revision, so a newer update shows again.
 */
export function EntryFeedNote({ eventId, feed }: { eventId: number; feed: FeedLike | null | undefined }) {
  const marker = feed ? `${feed.appliedRevision ?? ''}|${feed.lastFetchAt ?? ''}` : '';
  const [seen, setSeen] = useState<string | null>(() => readSeen(eventId));
  if (!feed) return null;

  if (feed.waiting) {
    return (
      <Badge className="bg-warning/15 text-warning" title={feed.lastMessage ?? undefined}>
        Entries need a decision
      </Badge>
    );
  }
  if (feed.lastStatus === 'AUTH_FAILED') {
    return <Badge className="bg-destructive/15 text-destructive">RaceHub key refused</Badge>;
  }
  if (feed.lastStatus === 'FAILED' && !feed.autoFetch && feed.lastMessage?.startsWith('The feed no longer serves')) {
    return (
      <Badge className="bg-warning/15 text-warning" title={feed.lastMessage}>
        No longer on RaceHub
      </Badge>
    );
  }
  if (feed.lastStatus === 'APPLIED' && feed.lastMessage && feed.lastFetchAt && seen !== marker) {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-info">
        Entries updated {formatDateTime(feed.lastFetchAt)}: {feed.lastMessage}
        <Button
          variant="ghost"
          size="icon"
          className="h-5 w-5"
          aria-label="Dismiss"
          onClick={() => {
            try {
              window.localStorage.setItem(seenKey(eventId), marker);
            } catch {
              // Not remembered, so it shows again next time; nothing else depends on it
            }
            setSeen(marker);
          }}
        >
          <X className="h-3 w-3" />
        </Button>
      </span>
    );
  }
  return null;
}
