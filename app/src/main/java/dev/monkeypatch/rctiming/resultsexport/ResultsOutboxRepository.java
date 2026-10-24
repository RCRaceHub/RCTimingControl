package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ResultsOutboxRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultsOutbox.RESULTS_OUTBOX;

/**
 * The queue of results exports waiting for RaceHub (#27), and the rules the rest of the export relies on.
 *
 * <p><strong>Delivery is at least once.</strong> An export is marked sent only after RaceHub accepts it, so a
 * crash, or a failure to record the success, sends it again. RaceHub tells the two apart by the
 * {@code Idempotency-Key} and the revision in the body ({@link ResultsSender}).
 *
 * <p><strong>Statuses.</strong> {@code QUEUED} and {@code FAILED} are both waiting to go: a failure is a
 * record of the last attempt and when to try next, never a giving up. Only {@code SENT} and
 * {@code SUPERSEDED} are final, and neither {@link #recordFailure} nor {@link #makeDue} touches those, so an
 * export already sent or replaced by a newer one is never sent afterwards.
 *
 * <p><strong>Order.</strong> Exports are read oldest id first, across every event, so one event's exports
 * always go in the order they were queued.
 */
@Repository
public class ResultsOutboxRepository extends JooqRepository<ResultsOutboxItem, ResultsOutboxRecord> {

    private static final ResultsOutboxItemMapper MAPPER = Mappers.getMapper(ResultsOutboxItemMapper.class);

    public ResultsOutboxRepository(DSLContext dsl) {
        super(dsl, RESULTS_OUTBOX, RESULTS_OUTBOX.ID);
    }

    @Override
    protected String entityName() {
        return "Results export";
    }

    /** The exports in the given statuses whose next attempt is due, oldest id first. */
    public List<ResultsOutboxItem> findByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
            Collection<OutboxStatus> statuses, Instant due) {
        return findWhere(RESULTS_OUTBOX.STATUS.in(names(statuses)).and(RESULTS_OUTBOX.NEXT_ATTEMPT_AT.le(due)));
    }

    /** Marks the event's exports still waiting to go as superseded by a newer one. */
    @Transactional
    public int supersedeWaiting(long eventId) {
        return dsl.update(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.STATUS, OutboxStatus.SUPERSEDED.name())
                .where(RESULTS_OUTBOX.EVENT_ID.eq(eventId).and(waiting()))
                .execute();
    }

    /** Makes a waiting export due now. One already sent or superseded is left alone. */
    @Transactional
    public int makeDue(long id, Instant now) {
        return dsl.update(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.NEXT_ATTEMPT_AT, now)
                .where(RESULTS_OUTBOX.ID.eq(id).and(waiting()))
                .execute();
    }

    /** Records a delivered export. It was sent, so this holds even if a newer one superseded it meanwhile. */
    @Transactional
    public int recordSent(long id, Instant sentAt) {
        return dsl.update(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.STATUS, OutboxStatus.SENT.name())
                .set(RESULTS_OUTBOX.SENT_AT, sentAt)
                .set(RESULTS_OUTBOX.ATTEMPTS, RESULTS_OUTBOX.ATTEMPTS.plus(1))
                .setNull(RESULTS_OUTBOX.LAST_ERROR)
                .where(RESULTS_OUTBOX.ID.eq(id))
                .execute();
    }

    /**
     * Records a failed attempt, only while the export is still waiting to go. One a newer export superseded
     * during the attempt stays superseded, so it is never sent after the newer one.
     */
    @Transactional
    public int recordFailure(long id, String error, Instant nextAttemptAt) {
        return dsl.update(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.STATUS, OutboxStatus.FAILED.name())
                .set(RESULTS_OUTBOX.ATTEMPTS, RESULTS_OUTBOX.ATTEMPTS.plus(1))
                .set(RESULTS_OUTBOX.LAST_ERROR, error)
                .set(RESULTS_OUTBOX.NEXT_ATTEMPT_AT, nextAttemptAt)
                .where(RESULTS_OUTBOX.ID.eq(id).and(waiting()))
                .execute();
    }

    private static Condition waiting() {
        return RESULTS_OUTBOX.STATUS.in(OutboxStatus.QUEUED.name(), OutboxStatus.FAILED.name());
    }

    private static List<String> names(Collection<OutboxStatus> statuses) {
        return statuses.stream().map(Enum::name).toList();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(RESULTS_OUTBOX.CREATED_AT);
    }

    @Override
    protected ResultsOutboxItem toEntity(ResultsOutboxRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ResultsOutboxItem i, ResultsOutboxRecord r) {
        MAPPER.toRecord(i, r);
    }

    @Override
    protected Long idOf(ResultsOutboxItem i) {
        return i.getId();
    }

    @Override
    protected void setId(ResultsOutboxItem i, Long id) {
        i.setId(id);
    }
}
