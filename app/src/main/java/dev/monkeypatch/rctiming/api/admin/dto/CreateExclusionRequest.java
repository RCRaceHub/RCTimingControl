package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/championships/{id}/exclusions}. {@code driverId} is the competitor's id, and
 * the reason is required (CHAMP-02) and kept for the audit trail (CHAMP-09).
 */
public record CreateExclusionRequest(
        @NotNull Long driverId,
        @NotNull Long eventId,
        @NotBlank String reason     // CHAMP-02 requires reason; CHAMP-09 persists it for audit
) {}
