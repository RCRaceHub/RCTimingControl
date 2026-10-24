package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;

/**
 * A racing class, the answer of the endpoints under {@code /api/v1/admin/classes}.
 */
public record RacingClassDto(
        Long id,
        String name,
        String description,
        String colour) {

    public static RacingClassDto from(RacingClass rc) {
        return new RacingClassDto(rc.getId(), rc.getName(), rc.getDescription(), rc.getColour());
    }
}
