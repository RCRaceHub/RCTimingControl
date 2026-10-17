package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /referee/race/{raceId}/incident-report}: the entry the incident is against, a free-text
 * type and a description, all required.
 */
public record IncidentReportRequest(
        @NotNull Long entryId,
        @NotBlank String incidentType,
        @NotBlank String description
) {}
