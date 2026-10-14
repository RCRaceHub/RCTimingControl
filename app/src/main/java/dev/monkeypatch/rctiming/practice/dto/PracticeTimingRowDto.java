package dev.monkeypatch.rctiming.practice.dto;

/**
 * DTO representing a single racer's timing row in a practice session.
 * Used for live STOMP broadcasts and REST snapshot responses.
 */
public record PracticeTimingRowDto(
        int position,
        String transponderNumber,
        Long userId,
        String racerName,
        int laps,
        Long bestLapMs,
        Long bestConsecutiveNMs,
        Long lastLapMs,
        int runs,
        boolean isUnknown
) {}
