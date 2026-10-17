package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * The body of {@code PUT /api/v1/admin/events/{eventId}/classes/{classId}/overrides}: a patch of format settings laid
 * over the class's snapshot. An empty map clears the override. The patch must still make a valid format.
 */
public record UpdateEventClassOverrideRequest(
        @NotNull Map<String, Object> override  // null map clears override; pass empty map to clear, non-empty to set
) {}
