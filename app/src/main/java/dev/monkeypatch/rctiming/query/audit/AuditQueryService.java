package dev.monkeypatch.rctiming.query.audit;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;

/** Reads the audit log (#138). Rows are only ever added, so this is the whole of what can be done with them. */
@Service
@ReadTransaction
public class AuditQueryService {

    /** The most rows one page may hold. */
    public static final int MAX_PAGE_SIZE = 200;

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public AuditQueryService(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    /** The rows that match, newest first. {@code page} counts from 0; {@code size} is capped at {@link #MAX_PAGE_SIZE}. */
    public AuditPageDto search(AuditFilter filter, int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Condition where = conditionFor(filter);

        long total = dsl.fetchCount(AUDIT_LOG, where);
        List<AuditEntryDto> entries = dsl.selectFrom(AUDIT_LOG)
                .where(where)
                .orderBy(AUDIT_LOG.OCCURRED_AT.desc(), AUDIT_LOG.ID.desc())
                .limit(pageSize)
                .offset(pageNumber * pageSize)
                .fetch(r -> new AuditEntryDto(
                        r.getId(), r.getOccurredAt(), r.getActorUserId(), r.getActorLabel(), r.getSource(),
                        r.getAction(), r.getEntityType(), r.getEntityId(), r.getEventId(), r.getRaceId(),
                        r.getSummary(), parse(r.getBeforeJson()), parse(r.getAfterJson())));
        return new AuditPageDto(entries, total, pageNumber, pageSize);
    }

    private static Condition conditionFor(AuditFilter f) {
        List<Condition> all = new ArrayList<>();
        if (f.entityType() != null) all.add(AUDIT_LOG.ENTITY_TYPE.eq(f.entityType()));
        if (f.entityId() != null) all.add(AUDIT_LOG.ENTITY_ID.eq(f.entityId()));
        if (f.action() != null) all.add(AUDIT_LOG.ACTION.eq(f.action()));
        if (f.actorUserId() != null) all.add(AUDIT_LOG.ACTOR_USER_ID.eq(f.actorUserId()));
        if (f.eventId() != null) all.add(AUDIT_LOG.EVENT_ID.eq(f.eventId()));
        if (f.raceId() != null) all.add(AUDIT_LOG.RACE_ID.eq(f.raceId()));
        if (f.from() != null) all.add(AUDIT_LOG.OCCURRED_AT.ge(f.from()));
        if (f.to() != null) all.add(AUDIT_LOG.OCCURRED_AT.lt(f.to()));
        return DSL.and(all);
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            // The column is checked with json_valid, so this is not expected; show the text rather than fail the page
            return objectMapper.getNodeFactory().textNode(json);
        }
    }
}
