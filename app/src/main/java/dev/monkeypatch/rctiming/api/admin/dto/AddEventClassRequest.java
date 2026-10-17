package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/events/{eventId}/classes}: the racing class to add and the race format
 * template whose config the event class takes a snapshot of.
 */
public record AddEventClassRequest(
        @NotNull Long racingClassId,
        @NotNull Long templateId
) {}
