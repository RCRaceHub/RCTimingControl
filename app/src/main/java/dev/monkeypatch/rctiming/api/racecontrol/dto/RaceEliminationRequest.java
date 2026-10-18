package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/** The body of the eliminate, revive and undo requests: which entry in the race. */
public record RaceEliminationRequest(@NotNull Long entryId) {
}
