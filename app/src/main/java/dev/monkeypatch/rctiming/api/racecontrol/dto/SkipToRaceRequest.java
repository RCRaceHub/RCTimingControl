package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/skip-to}: the race to move on to, which must be in the same
 * event.
 */
public record SkipToRaceRequest(
        @NotNull Long targetRaceId
) {
}
