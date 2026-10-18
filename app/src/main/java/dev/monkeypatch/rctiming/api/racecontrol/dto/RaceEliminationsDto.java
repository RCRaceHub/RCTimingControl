package dev.monkeypatch.rctiming.api.racecontrol.dto;

import java.util.List;

/** A race's eliminations, and whether it is a Shikabane race at all (race control hides the buttons if not). */
public record RaceEliminationsDto(boolean shikabane, List<RaceEliminationDto> rows) {
}
