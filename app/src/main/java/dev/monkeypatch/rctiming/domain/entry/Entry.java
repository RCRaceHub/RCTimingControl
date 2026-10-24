package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One driver's entry in one class of one event (table {@code entries}); it points at a {@link
 * dev.monkeypatch.rctiming.domain.competitor.Competitor} and carries the transponder numbers they race with in this
 * event. Entries come from the RaceHub import, the RC-Timing CSV import or are added as walk-ins.
 *
 * A competitor can hold only one active (not withdrawn) entry per event class, which the database enforces with a
 * unique index. A withdrawn entry is kept, never deleted. {@code externalSource}, {@code externalEntryId} and {@code
 * externalEntryVersion} identify an imported entry so a re-import updates it; all three are null for a walk-in.
 */
public class Entry implements UpdatedAt {

    private Long id;

    /** The driver this entry is for. Required on every new entry (L4, #12). */
    private Long competitorId;
    private Long eventId;
    private Long eventClassId;

    /**
     * The entry's primary transponder, as imported or typed in for a walk-in; a swap on the day replaces it.
     * Maps to the transponder_number column.
     */
    private String transponderNumberSnapshot;

    /** Optional second transponder for this entry (L6). Laps from either number count. */
    private String secondaryTransponderNumber;

    /** Optional third and fourth transponders (#482). Laps from any of the four numbers count. */
    private String thirdTransponderNumber;
    private String fourthTransponderNumber;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getThirdTransponderNumber() { return thirdTransponderNumber; }
    public void setThirdTransponderNumber(String thirdTransponderNumber) { this.thirdTransponderNumber = thirdTransponderNumber; }

    public String getFourthTransponderNumber() { return fourthTransponderNumber; }
    public void setFourthTransponderNumber(String fourthTransponderNumber) { this.fourthTransponderNumber = fourthTransponderNumber; }

    /** Every transponder number this entry races on, primary first, without the empty slots. */
    public List<String> allTransponderNumbers() {
        List<String> numbers = new ArrayList<>(4);
        for (String n : new String[] {transponderNumberSnapshot, secondaryTransponderNumber,
                thirdTransponderNumber, fourthTransponderNumber}) {
            if (n != null && !n.isBlank()) {
                numbers.add(n);
            }
        }
        return numbers;
    }

    /** True when {@code number} is any of this entry's transponders. */
    public boolean hasTransponder(String number) {
        return number != null && allTransponderNumbers().contains(number);
    }

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

}
