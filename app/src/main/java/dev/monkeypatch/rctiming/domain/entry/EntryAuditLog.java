package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/**
 * One change to an entry, by an official, with what it was before and after as JSON (table
 * {@code entry_audit_log}). The entry history ({@code EntryHistoryQuery}) reads these rows back.
 *
 * <p>The snapshots hold names and transponder numbers, so they are blanked when the competitor is anonymised.
 */
public class EntryAuditLog implements CreatedAt {

    private Long id;
    private Long entryId;
    private Long adminUserId;
    private String action;   // ADMIN_CREATE, ADMIN_WITHDRAW, TRANSPONDER_SWAP or COMPETITOR_MERGED
    private String reason;
    private String beforeSnapshot;
    private String afterSnapshot;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }

    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * A row ready to save.
     *
     * @param beforeJson the values before the change, from {@link #snapshot}, or null
     * @param afterJson  the values after it, or null
     */
    public static EntryAuditLog of(Long entryId, Long officialId, String action, String reason,
                                   String beforeJson, String afterJson, Instant at) {
        EntryAuditLog log = new EntryAuditLog();
        log.setEntryId(entryId);
        log.setAdminUserId(officialId);
        log.setAction(action);
        log.setReason(reason);
        log.setBeforeSnapshot(beforeJson);
        log.setAfterSnapshot(afterJson);
        log.setCreatedAt(at);
        return log;
    }

    /** The values as a JSON snapshot. A null value is written as JSON null, so pass a map that allows nulls. */
    public static String snapshot(ObjectMapper objectMapper, Map<String, ?> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize audit snapshot", e);
        }
    }
}
