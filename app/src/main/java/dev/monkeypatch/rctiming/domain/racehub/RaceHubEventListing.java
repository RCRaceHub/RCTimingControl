package dev.monkeypatch.rctiming.domain.racehub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * What RaceHub answers when RCTC asks which events are ready for timing: the club's events that were released to
 * timing and are not completed. Only the fields below are read; anything else in the document is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RaceHubEventListing(List<ListedEvent> events) {

    /**
     * One event RaceHub has released.
     *
     * @param id             RaceHub's id for the event, the same id its entry export carries as {@code event.id}
     * @param name           the event's name
     * @param eventLocalDate the day of the meeting as {@code yyyy-MM-dd}
     * @param venue          the venue's name, for information
     * @param entryCount     how many entries RaceHub holds, for information
     * @param entriesUrl     where this event's Entry Export v1 is fetched from, with the club's key
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ListedEvent(String id, String name, String eventLocalDate, String venue, Integer entryCount,
                              String entriesUrl) {
    }
}
