package dev.monkeypatch.rctiming.domain.race;


import java.time.Instant;

/**
 * A referee's report of an incident in a race (table {@code incident_reports}), raised against an entry. {@code
 * incidentType} and {@code description} are free text the referee typed.
 */
public class IncidentReport {

    private Long id;

    private Long raceId;

    private Long entryId;

    private String incidentType;

    private String description;

    private Long raisedBy;

    private Instant raisedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getRaisedBy() { return raisedBy; }
    public void setRaisedBy(Long raisedBy) { this.raisedBy = raisedBy; }

    public Instant getRaisedAt() { return raisedAt; }
    public void setRaisedAt(Instant raisedAt) { this.raisedAt = raisedAt; }
}
