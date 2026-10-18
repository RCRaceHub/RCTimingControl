package dev.monkeypatch.rctiming.domain.race;

import java.time.Instant;

/**
 * Penalty points given to a car in one Shikabane heat (table {@code race_point_penalties}), such as for a false
 * start. {@code points} is the number applied when it was given; it is added to the car's heat points. Timestamps
 * are UTC microseconds in the table.
 */
public class RacePointPenalty {

    private Long id;

    private Long raceId;

    private Long entryId;

    private int points;

    private String reason;

    private Long appliedBy;

    private Instant appliedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getAppliedBy() { return appliedBy; }
    public void setAppliedBy(Long appliedBy) { this.appliedBy = appliedBy; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
