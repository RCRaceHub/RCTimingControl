package dev.monkeypatch.rctiming.domain.race;


import java.time.Instant;

/**
 * A driver who missed their marshalling duty in a race (table {@code marshal_absences}), recorded by a referee. It may
 * then lead to a {@link MarshalPenalty}.
 */
public class MarshalAbsence {

    private Long id;

    private Long raceId;

    private Long entryId;

    private Long eventId;

    private Instant recordedAt;

    private Long recordedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }
}
