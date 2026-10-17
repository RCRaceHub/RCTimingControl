package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/marshal-adjustment}: one lap added to or taken off an entry.
 * {@code lapDelta} must be +1 or -1; anything else is refused with a 400.
 */
public record MarshalAdjustmentRequest(
        @NotNull Long entryId,
        @NotNull String transponderNumber,
        @NotNull Integer lapDelta  // +1 or -1
) {
}
