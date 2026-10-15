package dev.monkeypatch.rctiming.practice.dto;

/**
 * One driver's practice across a day (#151): every session they were timed in, merged by competitor, or by transponder
 * for a number nobody has named.
 */
public record PracticeDayRowDto(
        Long competitorId,
        String name,
        String transponderNumbers,
        int sessions,
        int runs,
        int laps,
        Long bestLapMs,
        Long bestConsecutiveNMs
) {}
