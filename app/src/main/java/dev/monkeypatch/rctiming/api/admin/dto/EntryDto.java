package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.entry.Entry;

import java.time.Instant;

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
