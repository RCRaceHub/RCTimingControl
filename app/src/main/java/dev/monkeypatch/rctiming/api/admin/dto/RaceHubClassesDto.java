package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassesService.ClubClass;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassesService.Listing;

import java.util.List;

/**
 * The club's RaceHub classes for the "RaceHub classes" screen. {@code error} is set, and {@code classes} empty, when
 * RaceHub could not be asked. {@code eventsUrl} is the saved events address (null when none) and {@code tokenSet}
 * says whether the club's key is set; the key is never sent.
 */
public record RaceHubClassesDto(List<RaceHubClassDto> classes, String error, String eventsUrl,
                                boolean tokenSet) {

    public static RaceHubClassesDto from(Listing listing) {
        return new RaceHubClassesDto(listing.classes().stream().map(RaceHubClassDto::from).toList(), listing.error(),
                listing.eventsUrl(), listing.tokenSet());
    }

    /** One class: RaceHub's fields, the racing class it is linked to, and a suggested one when it is not linked. */
    public record RaceHubClassDto(String id, String name, String rcClassName, boolean active,
                                  Long linkedRacingClassId, Long suggestedRacingClassId) {

        static RaceHubClassDto from(ClubClass c) {
            return new RaceHubClassDto(c.id(), c.name(), c.rcClassName(), c.active(),
                    c.linkedRacingClassId(), c.suggestedRacingClassId());
        }
    }
}
