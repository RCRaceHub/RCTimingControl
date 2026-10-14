package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.time.LocalDate;

public class Event implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private LocalDate eventDate;
    private EventStatus status = EventStatus.DRAFT;
    private Instant entryOpensAt;
    private Instant entryClosesAt;
    private Long trackId;
    private Instant createdAt;
    private Instant updatedAt;

    /** When entries were last imported from RaceHub (L8). Null if never. */
    private Instant racehubLastImportAt;

    /** The RaceHub export revision of that import. */
    private Long racehubLastRevision;

    /** The RaceHub event the imported entries came from (#27), so results can be sent back. Null if never imported. */
    private String racehubEventId;

    /** The last revision given to this event's results export (#27); each new export takes the next one. */
    private long resultsExportRevision;

    /**
     * Why the event's results need exporting, set alongside the change that calls for it and cleared when the
     * export is queued (#27): RACE_FINISHED, CORRECTION or DAY_CLOSE, or null when nothing is waiting.
     */
    private String resultsExportPending;

    /** Whether race control sends this event's live timing to the relay while its races run (#28). */
    private boolean liveFeedEnabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

    public Instant getEntryOpensAt() { return entryOpensAt; }
    public void setEntryOpensAt(Instant entryOpensAt) { this.entryOpensAt = entryOpensAt; }

    public Instant getEntryClosesAt() { return entryClosesAt; }
    public void setEntryClosesAt(Instant entryClosesAt) { this.entryClosesAt = entryClosesAt; }

    public Long getTrackId() { return trackId; }
    public void setTrackId(Long trackId) { this.trackId = trackId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getRacehubLastImportAt() { return racehubLastImportAt; }
    public void setRacehubLastImportAt(Instant racehubLastImportAt) { this.racehubLastImportAt = racehubLastImportAt; }

    public Long getRacehubLastRevision() { return racehubLastRevision; }
    public void setRacehubLastRevision(Long racehubLastRevision) { this.racehubLastRevision = racehubLastRevision; }

    public String getRacehubEventId() { return racehubEventId; }
    public void setRacehubEventId(String racehubEventId) { this.racehubEventId = racehubEventId; }

    public long getResultsExportRevision() { return resultsExportRevision; }
    void setResultsExportRevision(long resultsExportRevision) { this.resultsExportRevision = resultsExportRevision; }

    /** Takes the next results export revision. Load the event inside the transaction that saves it. */
    public long nextResultsExportRevision() { return ++resultsExportRevision; }

    public String getResultsExportPending() { return resultsExportPending; }
    public void setResultsExportPending(String resultsExportPending) { this.resultsExportPending = resultsExportPending; }

    public boolean isLiveFeedEnabled() { return liveFeedEnabled; }
    public void setLiveFeedEnabled(boolean liveFeedEnabled) { this.liveFeedEnabled = liveFeedEnabled; }
}
