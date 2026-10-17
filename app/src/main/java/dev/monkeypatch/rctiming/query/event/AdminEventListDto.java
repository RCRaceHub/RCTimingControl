package dev.monkeypatch.rctiming.query.event;

import dev.monkeypatch.rctiming.domain.event.EventStatus;

import java.time.LocalDate;

/**
 * One meeting in the admin list ({@link AdminEventQueryService}).
 *
 * @param status    the meeting's lifecycle state
 * @param trackName the track's name, or null when the meeting has no track
 */
public record AdminEventListDto(
        Long id,
        String name,
        LocalDate eventDate,
        EventStatus status,
        String trackName       // nullable — LEFT JOIN on tracks
) {}
