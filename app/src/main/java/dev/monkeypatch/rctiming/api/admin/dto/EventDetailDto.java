package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * An event with its classes, the answer of {@code GET /api/v1/admin/events/{id}}. {@code racehubLastImportAt} and
 * {@code racehubLastRevision} say when and at which revision RaceHub entries were last imported; both are null until the
 * first import. {@code racehubLinked} says the event is a RaceHub meeting, and {@code allowedTransitions} lists the
 * statuses the server will accept as the next move (in the order of {@link EventStatus}), so the screen never keeps
 * its own copy of the state machine.
 */
public record EventDetailDto(
        Long id,
        String name,
        LocalDate eventDate,
        EventStatus status,
        Long trackId,
        List<EventClassDto> classes,
        Instant racehubLastImportAt,
        Long racehubLastRevision,
        boolean racehubLinked,
        List<EventStatus> allowedTransitions
) {
    public static EventDetailDto from(Event e, List<EventClassDto> classes, Collection<EventStatus> allowedTransitions) {
        return new EventDetailDto(e.getId(), e.getName(), e.getEventDate(), e.getStatus(), e.getTrackId(), classes,
                e.getRacehubLastImportAt(), e.getRacehubLastRevision(), e.getRacehubEventId() != null,
                List.copyOf(allowedTransitions));
    }
}
