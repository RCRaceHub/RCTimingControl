package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One club class to link in {@code POST /api/v1/admin/racehub/class-links}: its RaceHub club class id and either an
 * existing racing class or the name of a new one.
 */
public record LinkRaceHubClassRequest(@NotBlank String clubClassId, Long racingClassId, String createName) {
}
