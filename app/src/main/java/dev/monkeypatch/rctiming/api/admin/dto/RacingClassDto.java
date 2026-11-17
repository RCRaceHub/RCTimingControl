package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;

/**
 * A racing class, the answer of the endpoints under {@code /api/v1/admin/classes}. {@code lastTemplateId} is the
 * format template the class was last entered with, or null when it never was; only the list fills it in.
 */
public record RacingClassDto(
        Long id,
        String name,
        String description,
        String colour,
        Long lastTemplateId) {

    public static RacingClassDto from(RacingClass rc) {
        return new RacingClassDto(rc.getId(), rc.getName(), rc.getDescription(), rc.getColour(), null);
    }

    public static RacingClassDto from(RacingClass rc, Long lastTemplateId) {
        return new RacingClassDto(rc.getId(), rc.getName(), rc.getDescription(), rc.getColour(), lastTemplateId);
    }
}
