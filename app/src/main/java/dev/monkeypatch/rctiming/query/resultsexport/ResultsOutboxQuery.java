package dev.monkeypatch.rctiming.query.resultsexport;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.resultsexport.OutboxStatus;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultsOutbox.RESULTS_OUTBOX;

/** The results exports queued for RaceHub, newest first, for the admin page (#27). */
@Component
@ReadTransaction
public class ResultsOutboxQuery {

    private final DSLContext dsl;

    public ResultsOutboxQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * One queued or sent export.
     *
     * @param revision      the export revision number for the event
     * @param reason        why it was queued, such as a race finishing or a correction
     * @param status        the delivery state, as the sender records it
     * @param attempts      how many times sending has been tried
     * @param nextAttemptAt when the sender will try next, if it is waiting to retry
     * @param lastError     the last failure message, or null
     * @param sentAt        when delivery succeeded, or null while unsent
     */
    public record OutboxRow(
            long id,
            long eventId,
            String eventName,
            long revision,
            String reason,
            String status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant sentAt) {
    }

    /** The most recent {@code limit} outbox rows across all events, newest first (by id). */
    public List<OutboxRow> latest(int limit) {
        return dsl.select(RESULTS_OUTBOX.ID, RESULTS_OUTBOX.EVENT_ID, EVENTS.NAME, RESULTS_OUTBOX.REVISION,
                        RESULTS_OUTBOX.REASON, RESULTS_OUTBOX.STATUS, RESULTS_OUTBOX.ATTEMPTS,
                        RESULTS_OUTBOX.NEXT_ATTEMPT_AT, RESULTS_OUTBOX.LAST_ERROR, RESULTS_OUTBOX.CREATED_AT,
                        RESULTS_OUTBOX.SENT_AT)
                .from(RESULTS_OUTBOX)
                .join(EVENTS).on(EVENTS.ID.eq(RESULTS_OUTBOX.EVENT_ID))
                .orderBy(RESULTS_OUTBOX.ID.desc())
                .limit(limit)
                .fetch(r -> new OutboxRow(
                        r.get(RESULTS_OUTBOX.ID),
                        r.get(RESULTS_OUTBOX.EVENT_ID),
                        r.get(EVENTS.NAME),
                        r.get(RESULTS_OUTBOX.REVISION),
                        r.get(RESULTS_OUTBOX.REASON),
                        r.get(RESULTS_OUTBOX.STATUS),
                        r.get(RESULTS_OUTBOX.ATTEMPTS),
                        r.get(RESULTS_OUTBOX.NEXT_ATTEMPT_AT),
                        r.get(RESULTS_OUTBOX.LAST_ERROR),
                        r.get(RESULTS_OUTBOX.CREATED_AT),
                        r.get(RESULTS_OUTBOX.SENT_AT)));
    }

    /**
     * How the outbox stands, for the race director's RaceHub indicator.
     *
     * @param waiting     exports not yet sent, queued or failed and waiting to retry
     * @param failed      the part of {@code waiting} whose last send was refused or did not get through
     * @param lastSentAt  when RaceHub last accepted an export, or null if none has been
     * @param lastError   the newest failure message among the waiting exports, or null
     */
    public record Summary(int waiting, int failed, Instant lastSentAt, String lastError) {
    }

    /** Counts the exports waiting and failed, and finds the last accepted send and the newest failure message. */
    public Summary summary() {
        int waiting = dsl.fetchCount(RESULTS_OUTBOX, RESULTS_OUTBOX.STATUS.in(OutboxStatus.QUEUED.name(), OutboxStatus.FAILED.name()));
        int failed = dsl.fetchCount(RESULTS_OUTBOX, RESULTS_OUTBOX.STATUS.eq(OutboxStatus.FAILED.name()));
        var newestSent = DSL.max(RESULTS_OUTBOX.SENT_AT);
        Instant lastSent = dsl.select(newestSent).from(RESULTS_OUTBOX).fetchOne(newestSent);
        String lastError = dsl.select(RESULTS_OUTBOX.LAST_ERROR).from(RESULTS_OUTBOX)
                .where(RESULTS_OUTBOX.STATUS.eq(OutboxStatus.FAILED.name()))
                .orderBy(RESULTS_OUTBOX.ID.desc()).limit(1)
                .fetchOne(RESULTS_OUTBOX.LAST_ERROR);
        return new Summary(waiting, failed, lastSent, lastError);
    }
}
