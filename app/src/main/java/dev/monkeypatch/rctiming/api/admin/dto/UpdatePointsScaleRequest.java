package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code PUT /api/v1/admin/championships/{id}/points-scale}: the whole new scale, at least one entry, which
 * replaces the old one.
 */
public record UpdatePointsScaleRequest(
        @NotNull @Size(min = 1) List<@Valid PointsScaleEntryDto> entries
) {}
