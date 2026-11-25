package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/add-entry}: add {@code entryId}, which is in no heat yet, to the
 * heat in the path.
 *
 * @param allRounds also add the entry to the same heat in every other practice and qualifying round where that heat
 *                  is still pending
 */
public record AddEntryToHeatRequest(
        @NotNull Long entryId,
        boolean allRounds
) {
}
