package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/move-entry}: move {@code entryId} out of the race in the path
 * into {@code targetRaceId}.
 *
 * @param allRounds also make the same move in every other practice and qualifying round where both heats are still
 *                  pending
 */
public record MoveEntryRequest(
        @NotNull Long entryId,
        @NotNull Long targetRaceId,
        boolean allRounds
) {
}
