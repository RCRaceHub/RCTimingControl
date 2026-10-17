package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/events/{eventId}/classes/combine}: the event classes (at least two, all in the
 * event) that will race together but score separately.
 */
public record CombineClassesRequest(
        @NotNull @Size(min = 2) List<Long> eventClassIds
) {}
