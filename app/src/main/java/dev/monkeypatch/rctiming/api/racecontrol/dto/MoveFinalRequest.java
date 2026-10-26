package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /race-control/race/{raceId}/move-final}: move the final in the path one place earlier
 * ({@code UP}) or later ({@code DOWN}) in the event's run order.
 */
public record MoveFinalRequest(@NotNull Direction direction) {

    /** Which way the final moves in the run order. */
    public enum Direction { UP, DOWN }
}
