package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/admin/championships/{id}/events}: the event to link and the round number it counts
 * as (1 or more) in the championship (CHAMP-10).
 */
public record AddChampionshipEventRequest(
        @NotNull Long eventId,
        @NotNull @Min(1) Integer roundNumber   // CHAMP-10
) {}
