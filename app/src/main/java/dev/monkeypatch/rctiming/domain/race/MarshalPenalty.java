package dev.monkeypatch.rctiming.domain.race;


import java.time.Instant;

public class MarshalPenalty {

    private Long id;

    private Long absenceId;

    private Long entryId;

    private Long eventId;

    private Long appliedBy;

    private Instant appliedAt;

    private String notes;

    private MarshalPenaltyKind kind = MarshalPenaltyKind.QUALIFYING_SCORE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAbsenceId() { return absenceId; }
    public void setAbsenceId(Long absenceId) { this.absenceId = absenceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getAppliedBy() { return appliedBy; }
    public void setAppliedBy(Long appliedBy) { this.appliedBy = appliedBy; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public MarshalPenaltyKind getKind() { return kind; }
    public void setKind(MarshalPenaltyKind kind) { this.kind = kind; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
