package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.RaceElimination;

import java.time.Instant;

/**
 * One row of a Shikabane race's eliminations as race control sees it. {@code revivedAt} is null while the car is
 * still dead; a row with a revival time is the record of a zombie revival.
 */
public record RaceEliminationDto(long entryId, Instant eliminatedAt, Instant revivedAt) {

    public static RaceEliminationDto from(RaceElimination e) {
        return new RaceEliminationDto(e.getEntryId(), e.getEliminatedAt(), e.getRevivedAt());
    }
}
