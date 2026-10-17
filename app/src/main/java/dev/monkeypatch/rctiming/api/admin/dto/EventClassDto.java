package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;

import java.util.Map;

/**
 * A racing class in an event, with the race format it runs. {@code configSnapshot} is the format copied from the
 * template when the class was added (FORMAT-06) and {@code configOverride} the patch laid over it for this event
 * (FORMAT-07), null if none. Event classes with the same {@code combinedRaceGroup} race together but score
 * separately; it is null otherwise.
 */
public record EventClassDto(
        Long id,
        Long eventId,
        Long racingClassId,
        Long templateId,
        RaceFormatConfig configSnapshot,
        Map<String, Object> configOverride,
        Long combinedRaceGroup
) {
    public static EventClassDto from(EventClass ec) {
        return new EventClassDto(
                ec.getId(),
                ec.getEventId(),
                ec.getRacingClassId(),
                ec.getTemplateId(),
                ec.getConfigSnapshot(),
                ec.getConfigOverride(),
                ec.getCombinedRaceGroup()
        );
    }
}
