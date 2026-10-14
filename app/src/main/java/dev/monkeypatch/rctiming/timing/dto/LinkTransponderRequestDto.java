package dev.monkeypatch.rctiming.timing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body (TIMING-08) for POST /api/v1/race-control/race/{raceId}/transponders/link.
 */
public record LinkTransponderRequestDto(
        @NotBlank @Size(max = 50) String transponderNumber,
        @NotNull Long entryId
) {}
