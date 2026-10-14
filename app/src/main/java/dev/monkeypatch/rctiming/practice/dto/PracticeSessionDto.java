package dev.monkeypatch.rctiming.practice.dto;

import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;

import java.time.Instant;

/**
 * DTO for PracticeSession entity.
 * Returned by create/start/stop endpoints and list queries.
 */
public record PracticeSessionDto(
        Long id,
        String name,
        Long eventId,
        String eventName,
        PracticeStatus status,
        Integer bestLapN,
        Integer pauseSeconds,
        Instant startedAt,
        Instant stoppedAt
) {}
