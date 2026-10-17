package dev.monkeypatch.rctiming.domain.race;


import java.math.BigDecimal;
import java.time.Instant;

/**
 * A referee's penalty against an entry in a race (table {@code penalties}). {@code value} is a number of laps for
 * {@link PenaltyType#LAP} and a number of seconds for {@link PenaltyType#TIME}.
 */
public class Penalty {

    private Long id;

    private Long raceId;

    private Long entryId;

    private PenaltyType penaltyType;

    private BigDecimal value;

    private String reason;

    private Long appliedBy;

    private Instant appliedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public PenaltyType getPenaltyType() { return penaltyType; }
    public void setPenaltyType(PenaltyType penaltyType) { this.penaltyType = penaltyType; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getAppliedBy() { return appliedBy; }
    public void setAppliedBy(Long appliedBy) { this.appliedBy = appliedBy; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
