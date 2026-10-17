package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.IncidentReport;

import java.time.Instant;

/**
 * An incident report as the referee raised it, the answer of {@code POST /referee/race/{raceId}/incident-report}.
 * {@code raisedBy} is the official's user id.
 */
public record IncidentReportDto(Long id, Long raceId, Long entryId, String incidentType, String description,
                                Long raisedBy, Instant raisedAt) {

    public static IncidentReportDto from(IncidentReport r) {
        return new IncidentReportDto(r.getId(), r.getRaceId(), r.getEntryId(), r.getIncidentType(),
                r.getDescription(), r.getRaisedBy(), r.getRaisedAt());
    }
}
