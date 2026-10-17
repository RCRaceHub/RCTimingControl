package dev.monkeypatch.rctiming.query.official;

import dev.monkeypatch.rctiming.jooq.generated.tables.Users;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.monkeypatch.rctiming.jooq.generated.Tables.OFFICIAL_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.Tables.USERS;
import static dev.monkeypatch.rctiming.jooq.generated.Tables.USER_ROLES;

/** Officials and the changes made to them, for the Officials page (#61). */
@Service
@ReadTransaction
public class OfficialQueryService {

    private final DSLContext dsl;

    public OfficialQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Every official, enabled ones first, then by first and last name (ties by id). Roles are read in a separate
     * statement, so they come from the same snapshot as the users only because the class is a read transaction.
     */
    public List<OfficialDto> listAll() {
        Map<Long, List<String>> roles = new LinkedHashMap<>();
        dsl.select(USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .from(USER_ROLES)
                .orderBy(USER_ROLES.ROLE.asc())
                .forEach(r -> roles.computeIfAbsent(r.get(USER_ROLES.USER_ID), id -> new ArrayList<>())
                        .add(r.get(USER_ROLES.ROLE)));
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.FIRST_NAME, USERS.LAST_NAME, USERS.DISABLED_AT, USERS.CREATED_AT)
                .from(USERS)
                .orderBy(DSL.when(USERS.DISABLED_AT.isNull(), 0).otherwise(1).asc(), USERS.FIRST_NAME.asc(), USERS.LAST_NAME.asc(), USERS.ID.asc())
                .fetch(r -> new OfficialDto(
                        r.get(USERS.ID),
                        r.get(USERS.EMAIL),
                        r.get(USERS.FIRST_NAME),
                        r.get(USERS.LAST_NAME),
                        roles.getOrDefault(r.get(USERS.ID), List.of()),
                        r.get(USERS.DISABLED_AT) == null,
                        r.get(USERS.DISABLED_AT),
                        r.get(USERS.CREATED_AT)));
    }

    /**
     * Changes to officials, newest first, {@code limit} at a time. {@code beforeId} is the id of the oldest
     * change already shown, so the next page starts after it; null starts at the newest. Rows are only ever
     * added and ids count up, so the id orders them and no change is skipped or shown twice between pages.
     */
    public List<OfficialChangeDto> changes(Long beforeId, int limit) {
        Users official = USERS.as("official");
        Users actor = USERS.as("actor");
        return dsl.select(OFFICIAL_AUDIT_LOG.ID, OFFICIAL_AUDIT_LOG.CREATED_AT, OFFICIAL_AUDIT_LOG.OFFICIAL_USER_ID,
                        official.FIRST_NAME, official.LAST_NAME, OFFICIAL_AUDIT_LOG.ACTION, OFFICIAL_AUDIT_LOG.DETAIL,
                        OFFICIAL_AUDIT_LOG.ACTOR_USER_ID, actor.FIRST_NAME, actor.LAST_NAME)
                .from(OFFICIAL_AUDIT_LOG)
                .join(official).on(official.ID.eq(OFFICIAL_AUDIT_LOG.OFFICIAL_USER_ID))
                .leftJoin(actor).on(actor.ID.eq(OFFICIAL_AUDIT_LOG.ACTOR_USER_ID))
                .where(beforeId == null ? DSL.noCondition() : OFFICIAL_AUDIT_LOG.ID.lt(beforeId))
                .orderBy(OFFICIAL_AUDIT_LOG.ID.desc())
                .limit(limit)
                .fetch(r -> new OfficialChangeDto(
                        r.get(OFFICIAL_AUDIT_LOG.ID),
                        r.get(OFFICIAL_AUDIT_LOG.CREATED_AT),
                        r.get(OFFICIAL_AUDIT_LOG.OFFICIAL_USER_ID),
                        r.get(official.FIRST_NAME) + " " + r.get(official.LAST_NAME),
                        r.get(OFFICIAL_AUDIT_LOG.ACTION),
                        r.get(OFFICIAL_AUDIT_LOG.DETAIL),
                        r.get(OFFICIAL_AUDIT_LOG.ACTOR_USER_ID),
                        r.get(OFFICIAL_AUDIT_LOG.ACTOR_USER_ID) == null ? null
                                : r.get(actor.FIRST_NAME) + " " + r.get(actor.LAST_NAME)));
    }
}
