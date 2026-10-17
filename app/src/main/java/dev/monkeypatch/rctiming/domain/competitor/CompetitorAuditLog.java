package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * One change to a competitor and who made it (table {@code competitor_audit_log}): today, how their name is said aloud.
 * It moves with the competitor when two are merged and is emptied of its values when one is anonymised.
 */
public class CompetitorAuditLog implements CreatedAt {

    public static final String SPOKEN_NAME_CHANGED = "SPOKEN_NAME_CHANGED";

    private Long id;
    private Long competitorId;
    private Long actorUserId;
    private String action;
    private String beforeValue;
    private String afterValue;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompetitorId() { return competitorId; }
    public void setCompetitorId(Long competitorId) { this.competitorId = competitorId; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    /** The value before the change; null when there was none. */
    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }

    /** The value after the change; null when it was cleared. */
    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
