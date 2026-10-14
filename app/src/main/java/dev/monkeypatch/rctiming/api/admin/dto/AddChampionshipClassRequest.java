package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddChampionshipClassRequest(
        @NotNull Long racingClassId,
        @Min(1) Integer bestXFromYX,          // nullable — inherits championship default
        @Min(1) Integer bestXFromYY
) {}
