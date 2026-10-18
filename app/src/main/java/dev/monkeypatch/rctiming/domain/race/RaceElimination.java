package dev.monkeypatch.rctiming.domain.race;

import java.time.Instant;

/**
 * A car marked dead in a Shikabane race (table {@code race_eliminations}). Race control records it by hand, since a
 * decoder cannot tell a dead car from a slow one. While {@code revivedAt} is null the row is a live elimination and
 * its {@code eliminatedAt} puts the car in the elimination order; once a zombie is revived the row stays as the
 * record of the revival (worth a bonus point) and no longer counts as an elimination. Timestamps are UTC
 * microseconds in the table.
 */
public class RaceElimination {

    private Long id;

    private Long raceId;

    private Long entryId;

    private Instant eliminatedAt;

    private Long eliminatedBy;

    private Instant revivedAt;

    private Long revivedBy;

    /** Whether the car is still dead, as opposed to a revival record. */
    public boolean isActive() { return revivedAt == null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Instant getEliminatedAt() { return eliminatedAt; }
    public void setEliminatedAt(Instant eliminatedAt) { this.eliminatedAt = eliminatedAt; }

    public Long getEliminatedBy() { return eliminatedBy; }
    public void setEliminatedBy(Long eliminatedBy) { this.eliminatedBy = eliminatedBy; }

    public Instant getRevivedAt() { return revivedAt; }
    public void setRevivedAt(Instant revivedAt) { this.revivedAt = revivedAt; }

    public Long getRevivedBy() { return revivedBy; }
    public void setRevivedBy(Long revivedBy) { this.revivedBy = revivedBy; }
}
