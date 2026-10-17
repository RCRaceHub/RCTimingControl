package dev.monkeypatch.rctiming.query.resultsexport;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
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
}
