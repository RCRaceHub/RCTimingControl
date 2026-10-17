package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.MarshalAbsence;

import java.time.Instant;

/**
 * A recorded marshal absence, the answer of {@code POST /referee/race/{raceId}/marshal-absent}; its id is what a
 * marshal penalty names. {@code recordedBy} is the official's user id.
 */
public record MarshalAbsenceDto(Long id, Long raceId, Long entryId, Long eventId, Instant recordedAt,
                                Long recordedBy) {

    public static MarshalAbsenceDto from(MarshalAbsence a) {
        return new MarshalAbsenceDto(a.getId(), a.getRaceId(), a.getEntryId(), a.getEventId(), a.getRecordedAt(),
                a.getRecordedBy());
    }
}
