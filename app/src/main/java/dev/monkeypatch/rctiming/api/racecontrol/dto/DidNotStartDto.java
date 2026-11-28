package dev.monkeypatch.rctiming.api.racecontrol.dto;

import java.util.List;

/** The entry ids of the cars marked as not started in a race, for race control to label its grid. */
public record DidNotStartDto(List<Long> entryIds) {
}
