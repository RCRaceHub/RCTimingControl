package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubEventsService;

import java.util.List;

/**
 * The "Find RaceHub events" picker's answer.
 *
 * @param raceHubUrl RaceHub's address in use, or null
 * @param tokenSet  whether the club has a key for RaceHub, saved in the app or in the settings file (the key itself is never sent)
 * @param events    the events RaceHub offers, empty when it could not be asked
 * @param error     why RaceHub could not be asked, or null
 */
public record RaceHubEventsDto(String raceHubUrl, boolean tokenSet, List<Offered> events, String error) {

    /** One offered event; {@code linkedEventId} is the RCTC event it is already linked to, or null. */
    public record Offered(String raceHubEventId, String name, String eventDate, String venue, Integer entryCount,
                          Long linkedEventId) {

        static Offered from(RaceHubEventsService.OfferedEvent e) {
            return new Offered(e.raceHubEventId(), e.name(), e.eventDate(), e.venue(), e.entryCount(),
                    e.linkedEventId());
        }
    }

    public static RaceHubEventsDto of(RaceHubEventsService.Settings settings, RaceHubEventsService.Listing listing) {
        return switch (listing) {
            case RaceHubEventsService.Offered offered -> new RaceHubEventsDto(settings.raceHubUrl(),
                    settings.tokenSet(), offered.events().stream().map(Offered::from).toList(), null);
            case RaceHubEventsService.Failure failure -> new RaceHubEventsDto(settings.raceHubUrl(),
                    settings.tokenSet(), List.of(), failure.message());
        };
    }
}
