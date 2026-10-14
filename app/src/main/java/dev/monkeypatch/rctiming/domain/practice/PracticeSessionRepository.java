package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeSessionsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeSessions.PRACTICE_SESSIONS;

@Repository
public class PracticeSessionRepository extends JooqRepository<PracticeSession, PracticeSessionsRecord> {

    public PracticeSessionRepository(DSLContext dsl) {
        super(dsl, PRACTICE_SESSIONS, PRACTICE_SESSIONS.ID);
    }

    /** The oldest running session. Nothing stops two being started, so this must not fail when there are more. */
    public Optional<PracticeSession> findRunningSession() {
        return findRunningSessions().stream().findFirst();
    }

    public List<PracticeSession> findRunningSessions() {
        return findWhere(PRACTICE_SESSIONS.STATUS.eq(PracticeStatus.RUNNING.name()));
    }

    /** The sessions that started at or after {@code from} and before {@code to}, oldest first. */
    public List<PracticeSession> findStartedBetween(Instant from, Instant to) {
        return findWhere(PRACTICE_SESSIONS.STARTED_AT.ge(from).and(PRACTICE_SESSIONS.STARTED_AT.lt(to)),
                PRACTICE_SESSIONS.STARTED_AT.asc(), PRACTICE_SESSIONS.ID.asc());
    }

    public List<PracticeSession> findByEventId(Long eventId) {
        return findWhere(PRACTICE_SESSIONS.EVENT_ID.eq(eventId));
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(PRACTICE_SESSIONS.CREATED_AT);
    }

    @Override
    protected PracticeSession toEntity(PracticeSessionsRecord r) {
        PracticeSession s = new PracticeSession();
        s.setId(r.getId());
        s.setName(r.getName());
        s.setEventId(r.getEventId());
        s.setStatus(r.getStatus() == null ? null : PracticeStatus.valueOf(r.getStatus()));
        s.setBestLapN(r.getBestLapN());
        s.setPauseSeconds(r.getPauseSeconds());
        s.setCreatedByUserId(r.getCreatedByUserId());
        s.setStartedAt(r.getStartedAt());
        s.setStoppedAt(r.getStoppedAt());
        s.setCreatedAt(r.getCreatedAt());
        s.setUpdatedAt(r.getUpdatedAt());
        return s;
    }

    @Override
    protected void toRecord(PracticeSession s, PracticeSessionsRecord r) {
        r.setName(s.getName());
        r.setEventId(s.getEventId());
        r.setStatus(s.getStatus() == null ? null : s.getStatus().name());
        r.setBestLapN(s.getBestLapN());
        r.setPauseSeconds(s.getPauseSeconds());
        r.setCreatedByUserId(s.getCreatedByUserId());
        r.setStartedAt(s.getStartedAt());
        r.setStoppedAt(s.getStoppedAt());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
    }

    @Override
    protected Long idOf(PracticeSession s) {
        return s.getId();
    }

    @Override
    protected void setId(PracticeSession s, Long id) {
        s.setId(id);
    }
}
