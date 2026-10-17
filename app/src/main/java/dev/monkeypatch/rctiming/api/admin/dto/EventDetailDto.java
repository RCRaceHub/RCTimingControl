package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An event with its classes, the answer of {@code GET /api/v1/admin/events/{id}}. {@code racehubLastImportAt} and
 * {@code racehubLastRevision} say when and at which revision RaceHub entries were last imported; both are null until the
 * first import.
 */
public record EventDetailDto(
        Long id,
        String name,
        LocalDate eventDate,
        EventStatus status,
        Long trackId,
        List<EventClassDto> classes,  // EventClassDto created in Task 2
        Instant racehubLastImportAt,
        Long racehubLastRevision
) {
    public static EventDetailDto from(Event e, List<EventClassDto> classes) {
        return new EventDetailDto(e.getId(), e.getName(), e.getEventDate(), e.getStatus(), e.getTrackId(), classes,
                e.getRacehubLastImportAt(), e.getRacehubLastRevision());
    }
}
