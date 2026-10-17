package dev.monkeypatch.rctiming.domain.audit;

import java.time.Instant;

/**
 * One row of the audit log (table {@code audit_log}): who did what to what, and when. See {@code V18__audit_log.sql}.
 * Rows are written by {@link AuditService} and never updated.
 *
 * <p>{@code actorUserId} is null for the command line, background jobs and anonymous callers, so
 * {@code actorLabel} holds the name as text at the time and the row still reads right if the official is later
 * renamed or deleted. {@code action} is an upper-case code such as {@code ENTRY_WITHDRAWN}; {@code entityType} and
 * {@code entityId} say what it happened to (the id is text because not every entity has a numeric one). {@code eventId}
 * and {@code raceId} are optional links used to filter an event's or race's history.
 */
public class AuditEntry {

    private Long id;
    /** When the row was written, as UTC microseconds in the database. */
    private Instant occurredAt;
    private Long actorUserId;
    private String actorLabel;
    private Actor.Source source;
    private String action;
    private String entityType;
    private String entityId;
    private Long eventId;
    private Long raceId;
    private String summary;
    private String beforeJson;
    private String afterJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public String getActorLabel() { return actorLabel; }
    public void setActorLabel(String actorLabel) { this.actorLabel = actorLabel; }

    public Actor.Source getSource() { return source; }
    public void setSource(Actor.Source source) { this.source = source; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    /** The values before the change as JSON, or null when there were none (a create) or they are not relevant. */
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }

    /** The values after the change as JSON, or null when there are none (a delete). */
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
}
