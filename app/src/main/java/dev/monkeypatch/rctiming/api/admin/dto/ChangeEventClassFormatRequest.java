package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /api/v1/admin/events/{eventId}/classes/{classId}/format}: the race format template whose
 * config the event class takes a new snapshot of.
 */
public record ChangeEventClassFormatRequest(
        @NotNull Long templateId
) {}
