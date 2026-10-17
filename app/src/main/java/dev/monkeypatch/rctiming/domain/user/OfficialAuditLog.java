package dev.monkeypatch.rctiming.domain.user;

import java.time.Instant;

/**
 * One change to an official's account, and who made it (#61; table {@code official_audit_log}). Never holds a
 * password. This is a separate log from the main audit log, written by {@link OfficialService}.
 */
public class OfficialAuditLog {

    /** What was changed. */
    public enum Action { ADDED, ROLES_CHANGED, PASSWORD_SET, DISABLED, ENABLED }

    private Long id;

    private Long officialUserId;

    /** The admin who made the change; null when it came from the laptop's command line. */
    private Long actorUserId;

    private String action;

    private String detail;

    private Instant createdAt;

    protected OfficialAuditLog() {
    }

    public OfficialAuditLog(Long officialUserId, Long actorUserId, Action action, String detail, Instant createdAt) {
        this.officialUserId = officialUserId;
        this.actorUserId = actorUserId;
        this.action = action.name();
        this.detail = detail;
        this.createdAt = createdAt;
    }

    /** For the repository: a row as stored. */
    OfficialAuditLog(Long id, Long officialUserId, Long actorUserId, String action, String detail, Instant createdAt) {
        this.id = id;
        this.officialUserId = officialUserId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    public Long getOfficialUserId() { return officialUserId; }
    public Long getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
