package dev.monkeypatch.rctiming.domain.race;


import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * A finished race's stored result (table {@code result_snapshots}), written once when the race finishes, since live
 * positions are not stored during a race. The JSON columns hold lists read and written with {@link ResultSnapshotJson}:
 * {@code positionsJson} the result rows, {@code lapHistoryJson} who was where after each lap.
 */
public class ResultSnapshot implements CreatedAt {

    private Long id;

    private Long raceId;

    private Instant finishedAt;

    /** The result after corrections: what results pages, championship points and the export read. */
    private String positionsJson;

    /** The result as timed when the race finished, before any corrections (#63). */
    private String timedPositionsJson;

    private String lapHistoryJson;

    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getPositionsJson() { return positionsJson; }
    public void setPositionsJson(String positionsJson) { this.positionsJson = positionsJson; }

    public String getTimedPositionsJson() { return timedPositionsJson; }
    public void setTimedPositionsJson(String timedPositionsJson) { this.timedPositionsJson = timedPositionsJson; }

    public String getLapHistoryJson() { return lapHistoryJson; }
    public void setLapHistoryJson(String lapHistoryJson) { this.lapHistoryJson = lapHistoryJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
