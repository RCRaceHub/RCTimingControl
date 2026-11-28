package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/** The body of the did-not-start and undo requests: which entry in the race. */
public record DidNotStartRequest(@NotNull Long entryId) {
}
