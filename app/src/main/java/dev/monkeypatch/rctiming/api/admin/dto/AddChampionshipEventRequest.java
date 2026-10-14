package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddChampionshipEventRequest(
        @NotNull Long eventId,
        @NotNull @Min(1) Integer roundNumber   // CHAMP-10
) {}
