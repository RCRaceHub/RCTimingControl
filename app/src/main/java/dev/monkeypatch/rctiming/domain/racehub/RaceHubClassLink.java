package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * Links a class in an imported file to one of the club's racing classes, once for the whole club (table
 * {@code racehub_class_links}). The key is the same one {@link RaceHubClassMapping} uses: RaceHub's event class id, or
 * {@code <source>:<id>} for another system. An import places a row in the event's class for that racing class, so
 * a club maps RaceHub's "2WD Buggy" once instead of at every meeting.
 */
public class RaceHubClassLink implements CreatedAt, UpdatedAt {

    private Long id;
    private String classKey;
    private Long racingClassId;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClassKey() { return classKey; }
    public void setClassKey(String classKey) { this.classKey = classKey; }

    public Long getRacingClassId() { return racingClassId; }
    public void setRacingClassId(Long racingClassId) { this.racingClassId = racingClassId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
