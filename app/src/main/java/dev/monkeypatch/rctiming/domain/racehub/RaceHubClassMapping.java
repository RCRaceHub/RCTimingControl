package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * Maps a class in an imported file to an event class here, when the names do not match (L7). The key is a
 * RaceHub event class id, or {@code CSV:<class name>} for a class in an RC-Timing driver CSV.
 */
public class RaceHubClassMapping implements CreatedAt, UpdatedAt {

    private Long id;
    private Long eventId;
    private String racehubEventClassId;
    private Long eventClassId;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getRacehubEventClassId() { return racehubEventClassId; }
    public void setRacehubEventClassId(String racehubEventClassId) { this.racehubEventClassId = racehubEventClassId; }

    public Long getEventClassId() { return eventClassId; }
    public void setEventClassId(Long eventClassId) { this.eventClassId = eventClassId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
