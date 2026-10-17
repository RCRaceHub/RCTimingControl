package dev.monkeypatch.rctiming.domain.championship;


import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * An official's decision to exclude one driver from one event of a championship (table {@code championship_exclusions},
 * CHAMP-02). That round then scores nothing for the driver, bonuses included. {@code driverId} is a competitor id and
 * {@code createdBy} the official who recorded it; the audit row written with it keeps the reason after a delete.
 */
public class ChampionshipExclusion implements CreatedAt {

    private Long id;

    private Long championshipId;

    private Long driverId;

    private Long eventId;

    private String reason;

    private Long createdBy;

    private Instant createdAt;

    public ChampionshipExclusion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChampionshipId() { return championshipId; }
    public void setChampionshipId(Long v) { this.championshipId = v; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long v) { this.driverId = v; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long v) { this.eventId = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
}
