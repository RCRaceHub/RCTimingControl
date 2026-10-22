package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/finals/move}: move {@code entryId} out of the final in the path
 * into {@code targetRaceId}, to grid place {@code targetPosition} (the back of the grid when null).
 */
public record FinalsMoveRequest(
        @NotNull Long entryId,
        @NotNull Long targetRaceId,
        @Min(1) Integer targetPosition
) {
}
