package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;

/** Repository for {@link EntryAuditLog} ({@code entry_audit_log}). */
@Repository
public class EntryAuditLogRepository extends JooqRepository<EntryAuditLog, EntryAuditLogRecord> {

    public EntryAuditLogRepository(DSLContext dsl) {
        super(dsl, ENTRY_AUDIT_LOG, ENTRY_AUDIT_LOG.ID);
    }

    /** The event's audit rows with {@code action}, oldest first. */
    public List<EntryAuditLog> findByEventIdAndAction(long eventId, String action) {
        return dsl.select(ENTRY_AUDIT_LOG.fields())
                .from(ENTRY_AUDIT_LOG)
                .join(ENTRIES).on(ENTRIES.ID.eq(ENTRY_AUDIT_LOG.ENTRY_ID))
                .where(ENTRIES.EVENT_ID.eq(eventId))
                .and(ENTRY_AUDIT_LOG.ACTION.eq(action))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT.asc(), ENTRY_AUDIT_LOG.ID.asc())
                .fetch(r -> toEntity(r.into(ENTRY_AUDIT_LOG)));
    }

    @Override
    protected EntryAuditLog toEntity(EntryAuditLogRecord r) {
        EntryAuditLog log = new EntryAuditLog();
        log.setId(r.getId());
        log.setEntryId(r.getEntryId());
        log.setAdminUserId(r.getAdminUserId());
        log.setAction(r.getAction());
        log.setReason(r.getReason());
        log.setBeforeSnapshot(r.getBeforeSnapshot());
        log.setAfterSnapshot(r.getAfterSnapshot());
        log.setCreatedAt(r.getCreatedAt());
        return log;
    }

    @Override
    protected void toRecord(EntryAuditLog log, EntryAuditLogRecord r) {
        r.setEntryId(log.getEntryId());
        r.setAdminUserId(log.getAdminUserId());
        r.setAction(log.getAction());
        r.setReason(log.getReason());
        r.setBeforeSnapshot(log.getBeforeSnapshot());
        r.setAfterSnapshot(log.getAfterSnapshot());
        r.setCreatedAt(log.getCreatedAt());
    }

    @Override
    protected Long idOf(EntryAuditLog log) {
        return log.getId();
    }

    @Override
    protected void setId(EntryAuditLog log, Long id) {
        log.setId(id);
    }
}
