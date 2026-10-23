package dev.monkeypatch.rctiming.practice.dto;

import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;

import java.time.Instant;

/**
 * DTO for PracticeSession entity.
 * Returned by create/start/stop endpoints and list queries.
 * {@code raceRunning} is true for an IDLE session while any race is RUNNING, when it can't be started (practice and a
 * race share the one decoder); the screens use it to disable Start with a hint. It is false for other statuses.
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
        Instant stoppedAt,
        boolean raceRunning
) {}
