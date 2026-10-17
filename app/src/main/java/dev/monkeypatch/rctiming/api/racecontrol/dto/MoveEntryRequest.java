package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

public record MoveEntryRequest(
        @NotNull Long entryId,
        @NotNull Long targetRaceId,
        boolean allRounds
) {
}
