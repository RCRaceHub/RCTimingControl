package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.util.Map;

/**
 * A racing class entered in one event (table {@code event_classes}): the event, the racing class, and the race format
 * it runs to. The format is a snapshot copied from a {@link RaceFormatTemplate} when the class was added, so editing
 * the template later changes nothing already set up (FORMAT-06). {@code configOverride} is an optional patch of
 * top-level fields merged over the snapshot at read time (FORMAT-07); use {@code RaceFormatService#getEffectiveConfig}
 * to get the merged result.
 *
 * Classes sharing a non-null {@code combinedRaceGroup} race together but are scored separately (EVENT-06). {@code
 * finalsCount} is the number of finals the round generator made for the class.
 */
public class EventClass implements CreatedAt, UpdatedAt {

    private Long id;
    private RaceFormatConfig configSnapshot;
    private Map<String, Object> configOverride;
    /** The template the config was copied from; null when the template has been deleted. */
    private Long templateId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long eventId;
    private Long racingClassId;
    private Long combinedRaceGroup;
    private Integer finalsCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RaceFormatConfig getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(RaceFormatConfig configSnapshot) { this.configSnapshot = configSnapshot; }

    public Map<String, Object> getConfigOverride() { return configOverride; }
    public void setConfigOverride(Map<String, Object> configOverride) { this.configOverride = configOverride; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getRacingClassId() { return racingClassId; }
    public void setRacingClassId(Long racingClassId) { this.racingClassId = racingClassId; }

    public Long getCombinedRaceGroup() { return combinedRaceGroup; }
    public void setCombinedRaceGroup(Long combinedRaceGroup) { this.combinedRaceGroup = combinedRaceGroup; }

    public Integer getFinalsCount() { return finalsCount; }
    public void setFinalsCount(Integer finalsCount) { this.finalsCount = finalsCount; }

}
