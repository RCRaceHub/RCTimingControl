package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

public class Entry implements UpdatedAt {

    private Long id;

    /** The racer's login on entries made in the racer portal, before it was removed (#18). Null on newer entries. */
    private Long userId;

    /** The driver this entry is for. Required on every new entry (L4, #12). */
    private Long competitorId;
    private Long eventId;
    private Long eventClassId;

    // The primary transponder as imported or typed in for a walk-in (RACER-07); a check-in swap replaces it.
    // The columns are transponder_number and transponder_label, with no _snapshot suffix.
    private String transponderNumberSnapshot;
    private String transponderLabelSnapshot;

    /** Optional second transponder for this entry (L6). Laps from either number count. */
    private String secondaryTransponderNumber;
    private EntryStatus status = EntryStatus.PENDING;
    private Instant submittedAt;
    private Instant confirmedAt;
    private Instant withdrawnAt;
    private Instant updatedAt;

    /** Where the entry was imported from (RACEHUB), or null for entries made here (L7). */
    private String externalSource;
    private String externalEntryId;

    /** The source's entry_version last applied. Only a higher version changes the entry. */
    private Long externalEntryVersion;

    /** RaceHub's race_day_status (NOT_ARRIVED / ARRIVED). Read-only here; set only by import. */
    private String racehubArrival;

    /**
     * The imported file's number for a transponder swapped on the day, when it differs from the number kept (#50).
     * Null when there is no difference.
     */
    private String importedTransponderNumber;
    private String importedSecondaryTransponderNumber;

    /** The RaceHub event_class_id this entry was booked in (#27). Null for walk-ins. */
    private String racehubEventClassId;

    /** When the competitor checked in at the desk (L11). Null until they do. */
    private Instant checkedInAt;

    /** The official who checked them in. */
    private Long checkedInByUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCompetitorId() { return competitorId; }
    public void setCompetitorId(Long competitorId) { this.competitorId = competitorId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getEventClassId() { return eventClassId; }
    public void setEventClassId(Long eventClassId) { this.eventClassId = eventClassId; }

    public String getTransponderNumberSnapshot() { return transponderNumberSnapshot; }
    public void setTransponderNumberSnapshot(String transponderNumberSnapshot) { this.transponderNumberSnapshot = transponderNumberSnapshot; }

    public String getSecondaryTransponderNumber() { return secondaryTransponderNumber; }
    public void setSecondaryTransponderNumber(String secondaryTransponderNumber) { this.secondaryTransponderNumber = secondaryTransponderNumber; }

    public String getTransponderLabelSnapshot() { return transponderLabelSnapshot; }
    public void setTransponderLabelSnapshot(String transponderLabelSnapshot) { this.transponderLabelSnapshot = transponderLabelSnapshot; }

    public EntryStatus getStatus() { return status; }
    public void setStatus(EntryStatus status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getExternalSource() { return externalSource; }
    public void setExternalSource(String externalSource) { this.externalSource = externalSource; }

    public String getExternalEntryId() { return externalEntryId; }
    public void setExternalEntryId(String externalEntryId) { this.externalEntryId = externalEntryId; }

    public Long getExternalEntryVersion() { return externalEntryVersion; }
    public void setExternalEntryVersion(Long externalEntryVersion) { this.externalEntryVersion = externalEntryVersion; }

    public String getRacehubArrival() { return racehubArrival; }
    public void setRacehubArrival(String racehubArrival) { this.racehubArrival = racehubArrival; }

    public String getImportedTransponderNumber() { return importedTransponderNumber; }
    public void setImportedTransponderNumber(String importedTransponderNumber) { this.importedTransponderNumber = importedTransponderNumber; }

    public String getImportedSecondaryTransponderNumber() { return importedSecondaryTransponderNumber; }
    public void setImportedSecondaryTransponderNumber(String importedSecondaryTransponderNumber) {
        this.importedSecondaryTransponderNumber = importedSecondaryTransponderNumber;
    }

    public String getRacehubEventClassId() { return racehubEventClassId; }
    public void setRacehubEventClassId(String racehubEventClassId) { this.racehubEventClassId = racehubEventClassId; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }

    public Long getCheckedInByUserId() { return checkedInByUserId; }
    public void setCheckedInByUserId(Long checkedInByUserId) { this.checkedInByUserId = checkedInByUserId; }
}
