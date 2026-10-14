package dev.monkeypatch.rctiming.practice.dto;

import java.time.LocalDate;
import java.util.List;

/** The day's practice: the sessions that started on it and each driver's totals across them. */
public record PracticeDayDto(
        LocalDate date,
        int bestLapN,
        List<PracticeSessionDto> sessions,
        List<PracticeDayRowDto> rows
) {}
