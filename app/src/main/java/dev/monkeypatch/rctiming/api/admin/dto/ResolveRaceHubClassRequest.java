package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One class to settle in {@code POST /api/v1/admin/events/{eventId}/racehub-class-links}: the held class key (the club class id) and
 * either an existing racing class or the name of a new one. {@code templateId} is the format for a class not yet in
 * the event, and may be null to use the format the class was last entered with.
 */
public record ResolveRaceHubClassRequest(
        @NotBlank String classKey,
        Long racingClassId,
        String createName,
        Long templateId) {
}
