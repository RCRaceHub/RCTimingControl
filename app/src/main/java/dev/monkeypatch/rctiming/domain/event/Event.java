package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A race meeting on one day at one track (table {@code events}). Its classes ({@code EventClass}), entries and rounds
 * hang off it by id. It moves through {@link EventStatus} under {@link EventStateMachineService}.
 *
 * The {@code racehub*} fields record the last entry import from RaceHub and the RaceHub event the entries came from;
 * the {@code resultsExport*} fields drive the results sent back to RaceHub (#27). {@code eventDate} is the local race
 * day, a date with no time zone.
 */
public class Event implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private LocalDate eventDate;
    private EventStatus status = EventStatus.DRAFT;
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

    /** Seconds between a race finishing and the next one starting while auto run is on (#338). */
    private int autoRunGapSeconds = 180;

    /** Whether auto run stops after the last qualifier so the finals can be checked first (#338). */
    private boolean autoRunHoldBeforeFinals = true;

    /** Whether auto run switches itself on when a race of the event is started by hand (#338). Off by default, so only Go starts it. */
    private boolean autoRunAutoStart = false;

    /** How practice is ranked on the live top board; fastest lap until race control chooses otherwise. */
    private PracticeRanking practiceRanking = PracticeRanking.FASTEST;

    /** Which finals are run in legs when the rounds are generated; the A final only until race control chooses. */
    private FinalLegsScope finalLegsScope = FinalLegsScope.A_ONLY;

    /**
     * When race control confirmed the finals line-ups on the review screen that follows qualifying, or null while
     * they are unconfirmed. Calculating the finals again clears it.
     */
    private Instant finalsLineupConfirmedAt;

    public Instant getFinalsLineupConfirmedAt() { return finalsLineupConfirmedAt; }
    public void setFinalsLineupConfirmedAt(Instant finalsLineupConfirmedAt) {
        this.finalsLineupConfirmedAt = finalsLineupConfirmedAt;
    }

    public FinalLegsScope getFinalLegsScope() { return finalLegsScope; }
    public void setFinalLegsScope(FinalLegsScope finalLegsScope) { this.finalLegsScope = finalLegsScope; }

    public PracticeRanking getPracticeRanking() { return practiceRanking; }
    public void setPracticeRanking(PracticeRanking practiceRanking) { this.practiceRanking = practiceRanking; }

    public int getAutoRunGapSeconds() { return autoRunGapSeconds; }
    public void setAutoRunGapSeconds(int autoRunGapSeconds) { this.autoRunGapSeconds = autoRunGapSeconds; }

    public boolean isAutoRunHoldBeforeFinals() { return autoRunHoldBeforeFinals; }
    public void setAutoRunHoldBeforeFinals(boolean autoRunHoldBeforeFinals) {
        this.autoRunHoldBeforeFinals = autoRunHoldBeforeFinals;
    }

    public boolean isAutoRunAutoStart() { return autoRunAutoStart; }
    public void setAutoRunAutoStart(boolean autoRunAutoStart) { this.autoRunAutoStart = autoRunAutoStart; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

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
