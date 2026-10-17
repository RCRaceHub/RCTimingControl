package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/championships/{id}/classes}: the racing class to add. A null
 * {@code bestXFromYX} or {@code bestXFromYY} takes the championship's own value.
 */
public record AddChampionshipClassRequest(
        @NotNull Long racingClassId,
        @Min(1) Integer bestXFromYX,          // nullable — inherits championship default
        @Min(1) Integer bestXFromYY
) {}
