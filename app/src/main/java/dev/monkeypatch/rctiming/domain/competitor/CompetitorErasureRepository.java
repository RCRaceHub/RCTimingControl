package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultsOutbox.RESULTS_OUTBOX;

/**
 * The statements {@link CompetitorAnonymiseService} (#263) runs across tables that have no entity of their own:
 * result snapshots, the RaceHub export outbox and the three audit logs. It does bulk reads and in-place rewrites,
 * so it does not extend {@code JooqRepository}. Every method runs inside the service's write transaction.
 */
@Repository
public class CompetitorErasureRepository {

    private static final List<String> LIVE_STATUSES =
            List.of(RaceStatus.GRID.name(), RaceStatus.RUNNING.name(), RaceStatus.STOPPED.name());

    /** A result snapshot's two stored JSON columns. */
    public record SnapshotJson(Long id, String positionsJson, String timedPositionsJson) {}

    /** A queued or sent RaceHub export payload. */
    public record OutboxPayload(Long id, String payload) {}

    private final DSLContext dsl;

    /** @param dsl the write connection's context, so every statement joins the service's transaction */
    public CompetitorErasureRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** The ids of every entry the competitor has, in any event. */
    public List<Long> entryIdsOf(Long competitorId) {
        return dsl.select(ENTRIES.ID).from(ENTRIES).where(ENTRIES.COMPETITOR_ID.eq(competitorId)).fetch(ENTRIES.ID);
    }

    /** Whether any of the entries is in a race that is on the grid, running or stopped. */
    public boolean inLiveRace(Collection<Long> entryIds) {
        return dsl.fetchExists(dsl.selectOne().from(RACE_ENTRIES).join(RACES)
                .on(RACES.ID.eq(RACE_ENTRIES.RACE_ID))
                .where(RACE_ENTRIES.ENTRY_ID.in(entryIds).and(RACES.STATUS.in(LIVE_STATUSES))));
    }

    /** The stored results of every race any of the entries took part in. */
    public List<SnapshotJson> snapshotsOfRacesWith(Collection<Long> entryIds) {
        return dsl.select(RESULT_SNAPSHOTS.ID, RESULT_SNAPSHOTS.POSITIONS_JSON,
                        RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON)
                .from(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.in(dsl.select(RACE_ENTRIES.RACE_ID).from(RACE_ENTRIES)
                        .where(RACE_ENTRIES.ENTRY_ID.in(entryIds))))
                .fetch(r -> new SnapshotJson(r.get(RESULT_SNAPSHOTS.ID), r.get(RESULT_SNAPSHOTS.POSITIONS_JSON),
                        r.get(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON)));
    }

    /** Rewrites a result snapshot's two JSON columns, with the competitor's name already removed by the caller. */
    @Transactional
    public void updateSnapshot(Long id, String positionsJson, String timedPositionsJson) {
        dsl.update(RESULT_SNAPSHOTS)
                .set(RESULT_SNAPSHOTS.POSITIONS_JSON, positionsJson)
                .set(RESULT_SNAPSHOTS.TIMED_POSITIONS_JSON, timedPositionsJson)
                .where(RESULT_SNAPSHOTS.ID.eq(id)).execute();
    }

    /** Every export payload, queued or sent. */
    public List<OutboxPayload> allOutboxPayloads() {
        return dsl.select(RESULTS_OUTBOX.ID, RESULTS_OUTBOX.PAYLOAD).from(RESULTS_OUTBOX)
                .fetch(r -> new OutboxPayload(r.get(RESULTS_OUTBOX.ID), r.get(RESULTS_OUTBOX.PAYLOAD)));
    }

    /** Rewrites one export payload, with the competitor's name already removed by the caller. */
    @Transactional
    public void updateOutboxPayload(Long id, String payload) {
        dsl.update(RESULTS_OUTBOX).set(RESULTS_OUTBOX.PAYLOAD, payload).where(RESULTS_OUTBOX.ID.eq(id)).execute();
    }

    /** Blanks the before and after values of the competitor's change history; returns the rows changed. */
    @Transactional
    public int blankCompetitorHistory(Long competitorId) {
        return dsl.update(COMPETITOR_AUDIT_LOG)
                .set(COMPETITOR_AUDIT_LOG.BEFORE_VALUE, (String) null)
                .set(COMPETITOR_AUDIT_LOG.AFTER_VALUE, (String) null)
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(competitorId)).execute();
    }

    /** Replaces the reason and drops the snapshots of the entries' change history; returns the rows changed. */
    @Transactional
    public int blankEntryHistory(Collection<Long> entryIds, String reason) {
        if (entryIds.isEmpty()) {
            return 0;
        }
        return dsl.update(ENTRY_AUDIT_LOG)
                .set(ENTRY_AUDIT_LOG.REASON, reason)
                .set(ENTRY_AUDIT_LOG.BEFORE_SNAPSHOT, (String) null)
                .set(ENTRY_AUDIT_LOG.AFTER_SNAPSHOT, (String) null)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.in(entryIds)).execute();
    }

    /**
     * Replaces the summary and drops the before and after JSON of every audit row about the competitor or their
     * entries, and of any row whose text holds one of {@code names}; returns the rows changed.
     */
    @Transactional
    public int blankAuditLog(Long competitorId, Collection<Long> entryIds, Collection<String> names,
                             String summary) {
        Condition aboutThem = AUDIT_LOG.ENTITY_TYPE.eq("competitor")
                .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(competitorId)));
        if (!entryIds.isEmpty()) {
            aboutThem = aboutThem.or(AUDIT_LOG.ENTITY_TYPE.eq("entry")
                    .and(AUDIT_LOG.ENTITY_ID.in(entryIds.stream().map(String::valueOf).toList())));
        }
        for (String text : names) {
            aboutThem = aboutThem.or(AUDIT_LOG.SUMMARY.contains(text)).or(AUDIT_LOG.BEFORE_JSON.contains(text))
                    .or(AUDIT_LOG.AFTER_JSON.contains(text));
        }
        return dsl.update(AUDIT_LOG)
                .set(AUDIT_LOG.SUMMARY, summary)
                .set(AUDIT_LOG.BEFORE_JSON, (String) null)
                .set(AUDIT_LOG.AFTER_JSON, (String) null)
                .where(aboutThem).execute();
    }
}
