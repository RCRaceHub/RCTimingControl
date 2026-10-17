package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * Where an event's entries are pulled from, and how the latest fetch went (#42), one row per event (table
 * {@code entry_feeds}, see V13). With {@code autoFetch} on, {@link EntryFeedScheduler} fetches it every few minutes.
 */
public class EntryFeed implements CreatedAt, UpdatedAt {

    private Long id;
    private Long eventId;
    private String url;
    /** The access token, encrypted; the plain token is never stored, logged or audited. */
    private String tokenEncrypted;
    /** The last four characters of the token, so an official can tell which one is saved; null for a short token. */
    private String tokenHint;
    private boolean autoFetch;
    private Instant lastFetchAt;
    private EntryFeedStatus lastStatus;
    private String lastError;
    /** The revision of the file last imported; fetching the same revision again changes nothing. */
    private Long appliedRevision;
    /** A fetched file waiting for an official to preview and confirm, with its revision in {@code heldRevision}. */
    private String heldDocument;
    private Long heldRevision;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTokenEncrypted() { return tokenEncrypted; }
    public void setTokenEncrypted(String tokenEncrypted) { this.tokenEncrypted = tokenEncrypted; }

    public String getTokenHint() { return tokenHint; }
    public void setTokenHint(String tokenHint) { this.tokenHint = tokenHint; }

    public boolean isAutoFetch() { return autoFetch; }
    public void setAutoFetch(boolean autoFetch) { this.autoFetch = autoFetch; }

    public Instant getLastFetchAt() { return lastFetchAt; }
    public void setLastFetchAt(Instant lastFetchAt) { this.lastFetchAt = lastFetchAt; }

    public EntryFeedStatus getLastStatus() { return lastStatus; }
    public void setLastStatus(EntryFeedStatus lastStatus) { this.lastStatus = lastStatus; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Long getAppliedRevision() { return appliedRevision; }
    public void setAppliedRevision(Long appliedRevision) { this.appliedRevision = appliedRevision; }

    public String getHeldDocument() { return heldDocument; }
    public void setHeldDocument(String heldDocument) { this.heldDocument = heldDocument; }

    public Long getHeldRevision() { return heldRevision; }
    public void setHeldRevision(Long heldRevision) { this.heldRevision = heldRevision; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
