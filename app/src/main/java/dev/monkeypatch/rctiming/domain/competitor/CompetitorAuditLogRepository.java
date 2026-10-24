package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.CompetitorAuditLogRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;

/** Repository for {@link CompetitorAuditLog} ({@code competitor_audit_log}). */
@Repository
public class CompetitorAuditLogRepository extends JooqRepository<CompetitorAuditLog, CompetitorAuditLogRecord> {

    private static final CompetitorAuditLogMapper MAPPER = Mappers.getMapper(CompetitorAuditLogMapper.class);

    public CompetitorAuditLogRepository(DSLContext dsl) {
        super(dsl, COMPETITOR_AUDIT_LOG, COMPETITOR_AUDIT_LOG.ID);
    }

    /** Moves one competitor's change history to another, as a merge does (#123). */
    @Transactional
    public void moveCompetitor(Long fromCompetitorId, Long toCompetitorId) {
        dsl.update(COMPETITOR_AUDIT_LOG)
                .set(COMPETITOR_AUDIT_LOG.COMPETITOR_ID, toCompetitorId)
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(fromCompetitorId))
                .execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(COMPETITOR_AUDIT_LOG.CREATED_AT);
    }

    @Override
    protected CompetitorAuditLog toEntity(CompetitorAuditLogRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(CompetitorAuditLog log, CompetitorAuditLogRecord r) {
        MAPPER.toRecord(log, r);
    }

    @Override
    protected Long idOf(CompetitorAuditLog log) {
        return log.getId();
    }

    @Override
    protected void setId(CompetitorAuditLog log, Long id) {
        log.setId(id);
    }
}
