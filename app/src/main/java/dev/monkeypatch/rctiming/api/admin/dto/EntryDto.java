package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.entry.Entry;

import java.time.Instant;

/**
 * An entry as the admin entry endpoints return it. {@code status} is {@code PENDING}, {@code CONFIRMED} or
 * {@code WITHDRAWN}; {@code transponderNumberSnapshot} is the primary transponder number saved on the entry. The
 * timestamps are null until that step happens.
 */
public record EntryDto(
        Long id,
        Long eventId,
        Long eventClassId,
        String transponderNumberSnapshot,
        String status,
        Instant submittedAt,
        Instant confirmedAt,
        Instant withdrawnAt) {

    public static EntryDto from(Entry e) {
        return new EntryDto(
                e.getId(),
                e.getEventId(),
                e.getEventClassId(),
                e.getTransponderNumberSnapshot(),
                e.getStatus().name(),
                e.getSubmittedAt(),
                e.getConfirmedAt(),
                e.getWithdrawnAt());
    }
}
