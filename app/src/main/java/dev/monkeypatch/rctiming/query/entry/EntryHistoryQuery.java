package dev.monkeypatch.rctiming.query.entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.audit.AuditActors;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Users.USERS;

/**
 * What has happened to one entry (#140), oldest first: the entry log (added by hand, withdrawn, transponder
 * swapped, moved by a competitor merge) together with the audit log's rows about the entry.
 * The older entry log keeps its own table, so the two are read side by side rather than copied.
 */
@Component
@ReadTransaction
public class EntryHistoryQuery {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public EntryHistoryQuery(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public List<EntryHistoryDto> forEntry(long entryId) {
        List<EntryHistoryDto> all = new ArrayList<>(entryLog(entryId));
        all.addAll(auditRows(entryId));
        all.sort(Comparator.comparing(EntryHistoryDto::at));
        return all;
    }

    private List<EntryHistoryDto> entryLog(long entryId) {
        Field<String> official = DSL.concat(USERS.FIRST_NAME, DSL.val(" "), USERS.LAST_NAME);
        return dsl.select(ENTRY_AUDIT_LOG.CREATED_AT, official, ENTRY_AUDIT_LOG.ACTION, ENTRY_AUDIT_LOG.REASON,
                        ENTRY_AUDIT_LOG.BEFORE_SNAPSHOT, ENTRY_AUDIT_LOG.AFTER_SNAPSHOT)
                .from(ENTRY_AUDIT_LOG)
                .leftJoin(USERS).on(USERS.ID.eq(ENTRY_AUDIT_LOG.ADMIN_USER_ID))
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(entryId))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT, ENTRY_AUDIT_LOG.ID)
                .fetch(r -> new EntryHistoryDto(r.value1(), r.value2(),
                        summaryOf(r.value3(), r.value5(), r.value6()), r.value4()));
    }

    private List<EntryHistoryDto> auditRows(long entryId) {
        return dsl.select(AUDIT_LOG.OCCURRED_AT, AUDIT_LOG.ACTOR_LABEL, AUDIT_LOG.SUMMARY)
                .from(AUDIT_LOG)
                .where(AUDIT_LOG.ENTITY_TYPE.eq("entry"))
                .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(entryId)))
                .orderBy(AUDIT_LOG.OCCURRED_AT, AUDIT_LOG.ID)
                .fetch(r -> new EntryHistoryDto(r.value1(), AuditActors.readable(r.value2()), r.value3(), null));
    }

    /** The entry log stores an action name and JSON snapshots; an unknown action shows as its own name. */
    String summaryOf(String action, String beforeJson, String afterJson) {
        return switch (action) {
            case "ADMIN_CREATE" -> "Added by hand as a walk-in";
            case "ADMIN_WITHDRAW" -> "Withdrawn";
            case "COMPETITOR_MERGED" -> "Moved to " + text(afterJson, "displayName", "another competitor")
                    + " when two competitors were merged";
            case "TRANSPONDER_SWAP" -> "Changed the " + slotName(text(afterJson, "slot", "")) + "transponder from "
                    + text(beforeJson, "transponderNumber", "none") + " to "
                    + text(afterJson, "transponderNumber", "none");
            default -> action;
        };
    }

    /** {@code PRIMARY} reads "primary ", and a missing slot reads as nothing, so "the transponder" still makes sense. */
    private static String slotName(String slot) {
        return slot.isEmpty() ? "" : slot.toLowerCase() + " ";
    }

    private String text(String json, String field, String fallback) {
        if (json == null) {
            return fallback;
        }
        try {
            JsonNode value = objectMapper.readTree(json).get(field);
            return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
        } catch (Exception e) {
            return fallback;
        }
    }
}
