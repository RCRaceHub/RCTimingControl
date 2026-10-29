package dev.monkeypatch.rctiming.domain.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Writes the audit log (#138): one place to read what officials and the system did.
 *
 * <pre>{@code
 * audit.entry(Actor.official(actorId), "ENTRY_WITHDRAWN")
 *      .entity("entry", entry.getId()).event(entry.getEventId())
 *      .summary("Withdrew Ada Lovelace from Buggy 2WD")
 *      .before(oldStatus).after(newStatus)
 *      .record();
 * }</pre>
 *
 * <p>{@link Builder#record()} is for a change an official makes: it must be called inside the transaction
 * that makes the change, and fails if there is none, so the row is committed with the change or not at all.
 * {@link Builder#recordStandalone()} is for events with no change to commit with, such as a failed sign-in.
 * Never put a password, token or other secret in a summary or in the before and after values.
 */
@Service
public class AuditService {

    private static final int LABEL_MAX = 200;
    private static final int ENTITY_ID_MAX = 64;
    private static final int SUMMARY_MAX = 500;

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public AuditService(AuditLogRepository repository, UserRepository userRepository, ObjectMapper objectMapper,
                        TransactionTemplate transactions) {
        this(repository, userRepository, objectMapper, transactions, Clock.systemUTC());
    }

    AuditService(AuditLogRepository repository, UserRepository userRepository, ObjectMapper objectMapper,
                 TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Starts a row for {@code action} (in capitals, such as {@code LOGIN_FAILED}) done by {@code actor}. */
    public Builder entry(Actor actor, String action) {
        return new Builder(actor, action);
    }

    /**
     * Collects the parts of one audit row. Only the summary is required ({@link #record()} and
     * {@link #recordStandalone()} throw {@link IllegalArgumentException} without one); an entity type left unset is
     * {@code system}. Over-long labels, entity ids and summaries are truncated rather than refused, so auditing can
     * never make the change it describes fail.
     */
    public final class Builder {
        private final Actor actor;
        private final String action;
        private String entityType = "system";
        private String entityId;
        private Long eventId;
        private Long raceId;
        private String summary;
        private Object before;
        private Object after;

        private Builder(Actor actor, String action) {
            this.actor = actor;
            this.action = action;
        }

        /** What it happened to, for example {@code entity("official", 17)}. */
        public Builder entity(String type, Object id) {
            this.entityType = type;
            this.entityId = id == null ? null : String.valueOf(id);
            return this;
        }

        public Builder event(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder race(Long raceId) {
            this.raceId = raceId;
            return this;
        }

        /** One line a person can read. */
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        /** The value before the change: anything Jackson can write as JSON (a map, a record, a string). */
        public Builder before(Object before) {
            this.before = before;
            return this;
        }

        /** The value after the change. */
        public Builder after(Object after) {
            this.after = after;
            return this;
        }

        /**
         * Writes the row in the current transaction, which must exist: it is the change's own.
         *
         * @throws IllegalTransactionStateException when no transaction is active
         */
        public AuditEntry record() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalTransactionStateException(
                        "An audit row must be written in the transaction of the change it describes: " + action);
            }
            return insert(this);
        }

        /**
         * Writes the row in a transaction of its own (or the current one), for events with no change to commit with.
         * Runs on the write connection like any other write.
         */
        public AuditEntry recordStandalone() {
            return transactions.execute(status -> insert(this));
        }
    }

    private AuditEntry insert(Builder b) {
        if (b.summary == null || b.summary.isBlank()) {
            throw new IllegalArgumentException("An audit row needs a summary: " + b.action);
        }
        AuditEntry e = new AuditEntry();
        e.setOccurredAt(clock.instant());
        e.setActorUserId(b.actor.userId());
        e.setActorLabel(truncate(labelOf(b.actor), LABEL_MAX));
        e.setSource(b.actor.source());
        e.setAction(b.action);
        e.setEntityType(b.entityType);
        e.setEntityId(truncate(b.entityId, ENTITY_ID_MAX));
        e.setEventId(b.eventId);
        e.setRaceId(b.raceId);
        e.setSummary(truncate(b.summary, SUMMARY_MAX));
        e.setBeforeJson(toJson(b.before));
        e.setAfterJson(toJson(b.after));
        return repository.save(e);
    }

    /** The official's name and email at the time, kept as text so the row still reads right if they are renamed. */
    private String labelOf(Actor actor) {
        if (actor.label() != null) {
            return actor.label();
        }
        return userRepository.findById(actor.userId())
                .map(AuditService::describe)
                .orElse("official #" + actor.userId());
    }

    private static String describe(User user) {
        return (user.getFirstName() + " " + user.getLastName()).strip() + " <" + user.getEmail() + ">";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Could not write the audit values as JSON", ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
