package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.OfficialAuditLogRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.OfficialAuditLog.OFFICIAL_AUDIT_LOG;

/**
 * Repository for {@link OfficialAuditLog} ({@code official_audit_log}). Mapped by hand, not with MapStruct: the entity
 * is built by constructor and has no setters, so there is nothing for a generated mapper to fill.
 */
@Repository
public class OfficialAuditLogRepository extends JooqRepository<OfficialAuditLog, OfficialAuditLogRecord> {

    public OfficialAuditLogRepository(DSLContext dsl) {
        super(dsl, OFFICIAL_AUDIT_LOG, OFFICIAL_AUDIT_LOG.ID);
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(OFFICIAL_AUDIT_LOG.CREATED_AT);
    }

    @Override
    protected OfficialAuditLog toEntity(OfficialAuditLogRecord r) {
        return new OfficialAuditLog(r.getId(), r.getOfficialUserId(), r.getActorUserId(), r.getAction(),
                r.getDetail(), r.getCreatedAt());
    }

    @Override
    protected void toRecord(OfficialAuditLog a, OfficialAuditLogRecord r) {
        r.setOfficialUserId(a.getOfficialUserId());
        r.setActorUserId(a.getActorUserId());
        r.setAction(a.getAction());
        r.setDetail(a.getDetail());
        r.setCreatedAt(a.getCreatedAt());
    }

    @Override
    protected Long idOf(OfficialAuditLog a) {
        return a.getId();
    }

    @Override
    protected void setId(OfficialAuditLog a, Long id) {
        a.setId(id);
    }
}
