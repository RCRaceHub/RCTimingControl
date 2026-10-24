package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A walk-in entry added by hand (L9, #17). Give either an existing {@code competitorId} or a
 * {@code competitorName} for a new competitor. A new name that matches an existing competitor is
 * refused (409, with the matches) unless {@code confirmNewCompetitor} says it is a different person (#123).
 * The body of {@code POST /api/v1/admin/entries}. {@code primaryTransponder} is required; a blank
 * {@code secondaryTransponder}, {@code thirdTransponder} or {@code fourthTransponder} means none; a third needs a
 * secondary and a fourth needs a third (#482).
 */
public record AdminCreateEntryRequest(
        @NotNull Long eventId,
        @NotNull Long eventClassId,
        Long competitorId,
        @Size(max = 255) String competitorName,
        @NotBlank @Size(max = 20) String primaryTransponder,
        @Size(max = 20) String secondaryTransponder,
        Boolean confirmNewCompetitor,
        @Size(max = 20) String thirdTransponder,
        @Size(max = 20) String fourthTransponder) {

    /** A request with at most two transponders. */
    public AdminCreateEntryRequest(Long eventId, Long eventClassId, Long competitorId, String competitorName,
                                   String primaryTransponder, String secondaryTransponder,
                                   Boolean confirmNewCompetitor) {
        this(eventId, eventClassId, competitorId, competitorName, primaryTransponder, secondaryTransponder,
                confirmNewCompetitor, null, null);
    }
}
