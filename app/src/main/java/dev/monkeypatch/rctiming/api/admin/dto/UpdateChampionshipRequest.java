package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/admin/championships/{id}}; the fields mean the same as in
 * {@link CreateChampionshipRequest}.
 */
public record UpdateChampionshipRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(1) Integer bestXFromYX,
        @Min(1) Integer bestXFromYY,
        @Min(0) int tqBonusPoints,
        @Min(0) int afinalWinnerBonusPoints
) {}
