package dev.monkeypatch.rctiming.domain.racehub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One race format of the club's, as RaceHub's {@code GET /api/rctc/v1/formats} lists it. Only the fields below are
 * read.
 *
 * @param id     the club format id, the same value an Entry Export carries as {@code event.race_format_id}
 * @param name   the format's name as the organiser sees it
 * @param active false once the club stops offering the format; old meetings still refer to it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RaceHubFormatList(String id, String name, Boolean active) {
}
