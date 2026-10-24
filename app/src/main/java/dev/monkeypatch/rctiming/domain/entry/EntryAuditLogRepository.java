package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;

/** Repository for {@link EntryAuditLog} ({@code entry_audit_log}). */
@Repository
public class EntryAuditLogRepository extends JooqRepository<EntryAuditLog, EntryAuditLogRecord> {

    private static final EntryAuditLogMapper MAPPER = Mappers.getMapper(EntryAuditLogMapper.class);

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
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(EntryAuditLog log, EntryAuditLogRecord r) {
        MAPPER.toRecord(log, r);
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
