package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeed;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedStatus;
import java.time.Instant;

/**
 * The feed as the page shows it. The token itself is never sent back: only whether one is saved and its last
 * four characters. {@code lastFetchAt}, {@code lastStatus} and {@code lastMessage} describe the latest fetch;
 * {@code appliedRevision} is the revision last imported; {@code waiting} says a fetched file is held for an
 * official to confirm, and {@code waitingRevision} is its revision.
 */
public record EntryFeedDto(String url, boolean tokenSaved, String tokenHint, boolean autoFetch,
                           Instant lastFetchAt, EntryFeedStatus lastStatus, String lastMessage,
                           Long appliedRevision, boolean waiting, Long waitingRevision) {
    public static EntryFeedDto of(EntryFeed f) {
        return new EntryFeedDto(f.getUrl(), f.getTokenEncrypted() != null, f.getTokenHint(), f.isAutoFetch(),
                f.getLastFetchAt(), f.getLastStatus(), f.getLastError(), f.getAppliedRevision(),
                f.getHeldDocument() != null, f.getHeldRevision());
    }
}
