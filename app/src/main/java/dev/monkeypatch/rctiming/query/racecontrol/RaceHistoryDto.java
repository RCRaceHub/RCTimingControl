package dev.monkeypatch.rctiming.query.racecontrol;

import java.time.Instant;

/**
 * One thing that happened in a race, as the race director or referee reads it.
 *
 * @param at     when it happened
 * @param kind  what sort of thing it was: {@code LIFECYCLE}, {@code PENALTY}, {@code INCIDENT}, {@code MARSHAL_LAP},
 *               {@code MARSHAL_ABSENCE}, {@code MARSHAL_PENALTY}, {@code JUMP_START}, {@code TRANSPONDER_LINK} or {@code OTHER}
 * @param summary a sentence saying what happened
 * @param actor  the official, or the system job, that did it; null when the record never held one
 * @param driver the driver it was about, or null when it was about the whole race
 */
public record RaceHistoryDto(Instant at, String kind, String actor, String driver, String summary) {
}
