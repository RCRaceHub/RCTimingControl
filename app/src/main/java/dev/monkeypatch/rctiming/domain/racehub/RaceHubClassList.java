package dev.monkeypatch.rctiming.domain.racehub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One class of the club's, as RaceHub's {@code GET /api/rctc/v1/classes} lists it. Only the fields below are read.
 *
 * @param id            the club class id, the same value an Entry Export carries as {@code club_class_id}
 * @param name          the class name shown to racers
 * @param rcClassName   the class's name for race control
 * @param active        false once the club stops offering the class; old meetings still refer to it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RaceHubClassList(String id, String name, String rcClassName, Boolean active) {
}
