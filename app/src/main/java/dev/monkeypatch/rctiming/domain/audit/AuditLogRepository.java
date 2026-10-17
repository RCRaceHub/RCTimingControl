package dev.monkeypatch.rctiming.domain.audit;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.AuditLogRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;

/**
 * Repository for {@link AuditEntry} ({@code audit_log}). Writes audit rows. Reads go through {@code AuditQueryService}. Rows are never updated; the only deletes are the
 * retention job's, by age (#262).
 */
@Repository
public class AuditLogRepository extends JooqRepository<AuditEntry, AuditLogRecord> {

    public AuditLogRepository(DSLContext dsl) {
        super(dsl, AUDIT_LOG, AUDIT_LOG.ID);
    }

    /** Deletes the rows of these actions written before {@code cutoff}; returns how many went. */
    @Transactional
    public int deleteActionsOlderThan(Collection<String> actions, Instant cutoff) {
        return dsl.deleteFrom(AUDIT_LOG)
                .where(AUDIT_LOG.ACTION.in(actions).and(AUDIT_LOG.OCCURRED_AT.lt(cutoff)))
                .execute();
    }

    /** Deletes every row, of any action, written before {@code cutoff}; returns how many went. */
    @Transactional
    public int deleteAllOlderThan(Instant cutoff) {
        return dsl.deleteFrom(AUDIT_LOG).where(AUDIT_LOG.OCCURRED_AT.lt(cutoff)).execute();
    }

    @Override
    protected AuditEntry toEntity(AuditLogRecord r) {
        AuditEntry e = new AuditEntry();
        e.setId(r.getId());
        e.setOccurredAt(r.getOccurredAt());
        e.setActorUserId(r.getActorUserId());
        e.setActorLabel(r.getActorLabel());
        e.setSource(Actor.Source.valueOf(r.getSource()));
        e.setAction(r.getAction());
        e.setEntityType(r.getEntityType());
        e.setEntityId(r.getEntityId());
        e.setEventId(r.getEventId());
        e.setRaceId(r.getRaceId());
        e.setSummary(r.getSummary());
        e.setBeforeJson(r.getBeforeJson());
        e.setAfterJson(r.getAfterJson());
        return e;
    }

    @Override
    protected void toRecord(AuditEntry e, AuditLogRecord r) {
        r.setOccurredAt(e.getOccurredAt());
        r.setActorUserId(e.getActorUserId());
        r.setActorLabel(e.getActorLabel());
        r.setSource(e.getSource().name());
        r.setAction(e.getAction());
        r.setEntityType(e.getEntityType());
        r.setEntityId(e.getEntityId());
        r.setEventId(e.getEventId());
        r.setRaceId(e.getRaceId());
        r.setSummary(e.getSummary());
        r.setBeforeJson(e.getBeforeJson());
        r.setAfterJson(e.getAfterJson());
    }

    @Override
    protected Long idOf(AuditEntry e) {
        return e.getId();
    }

    @Override
    protected void setId(AuditEntry e, Long id) {
        e.setId(id);
    }
}
