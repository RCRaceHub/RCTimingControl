package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/finals/swap}: swap {@code entryId} in the final in the path
 * with {@code otherEntryId} in {@code otherRaceId}, which may be the same final.
 */
public record FinalsSwapRequest(
        @NotNull Long entryId,
        @NotNull Long otherRaceId,
        @NotNull Long otherEntryId
) {
}
