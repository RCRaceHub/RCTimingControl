package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeed;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedStatus;

import java.time.Instant;

/**
 * One event's entry feed in short, for the Events list: enough to say "entries updated", "needs a decision" or "not
 * on RaceHub any more" beside the event without opening it. Nothing secret: no address and no token.
 *
 * @param eventId         the event the feed belongs to
 * @param autoFetch       whether it is fetched automatically
 * @param lastFetchAt     when it was last fetched (UTC), or null
 * @param lastStatus      the outcome of the last fetch, or null
 * @param lastMessage     what the last fetch did or why it failed, or null
 * @param appliedRevision the revision last imported, or null
 * @param waiting         whether a fetched file waits for an official
 */
public record EntryFeedSummaryDto(long eventId, boolean autoFetch, Instant lastFetchAt, EntryFeedStatus lastStatus,
                                  String lastMessage, Long appliedRevision, boolean waiting) {

    public static EntryFeedSummaryDto of(EntryFeed f) {
        return new EntryFeedSummaryDto(f.getEventId(), f.isAutoFetch(), f.getLastFetchAt(), f.getLastStatus(),
                f.getLastError(), f.getAppliedRevision(), f.getHeldDocument() != null);
    }
}
