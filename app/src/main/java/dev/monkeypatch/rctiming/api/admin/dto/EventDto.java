package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventStatus;

import java.time.LocalDate;

/**
 * An event's own fields, the answer of the create, update and transition endpoints under
 * {@code /api/v1/admin/events}. {@code trackId} is null when no track is set.
 */
public record EventDto(
        Long id,
        String name,
        LocalDate eventDate,
        EventStatus status,
        Long trackId
) {
    public static EventDto from(Event e) {
        return new EventDto(e.getId(), e.getName(), e.getEventDate(), e.getStatus(), e.getTrackId());
    }
}
