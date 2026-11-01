package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassLinkService.Resolved;

/** Where a settled class now lands: the club's racing class, the event's class, and whether the class is new. */
public record ResolvedRaceHubClassDto(String racehubEventClassId, Long racingClassId, Long eventClassId,
                                      boolean racingClassCreated) {

    public static ResolvedRaceHubClassDto from(Resolved r) {
        return new ResolvedRaceHubClassDto(r.racehubEventClassId(), r.racingClassId(), r.eventClassId(),
                r.racingClassCreated());
    }
}
