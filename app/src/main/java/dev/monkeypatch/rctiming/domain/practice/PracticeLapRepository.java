package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeLapsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeLaps.PRACTICE_LAPS;

@Repository
public class PracticeLapRepository extends JooqRepository<PracticeLap, PracticeLapsRecord> {

    public PracticeLapRepository(DSLContext dsl) {
        super(dsl, PRACTICE_LAPS, PRACTICE_LAPS.ID);
    }

    public List<PracticeLap> findByPracticeSessionIdOrderByCrossingTimeAsc(Long practiceSessionId) {
        return findWhere(PRACTICE_LAPS.PRACTICE_SESSION_ID.eq(practiceSessionId),
                PRACTICE_LAPS.CROSSING_TIME.asc(), PRACTICE_LAPS.ID.asc());
    }

    public List<PracticeLap> findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(
            Long practiceSessionId, String transponderNumber) {
        return findWhere(PRACTICE_LAPS.PRACTICE_SESSION_ID.eq(practiceSessionId)
                        .and(PRACTICE_LAPS.TRANSPONDER_NUMBER.eq(transponderNumber)),
                PRACTICE_LAPS.LAP_NUMBER.asc(), PRACTICE_LAPS.ID.asc());
    }

    /** The laps of the sessions, in the order they were crossed. */
    public List<PracticeLap> findByPracticeSessionIds(Collection<Long> practiceSessionIds) {
        return practiceSessionIds.isEmpty() ? List.of()
                : findWhere(PRACTICE_LAPS.PRACTICE_SESSION_ID.in(practiceSessionIds),
                        PRACTICE_LAPS.CROSSING_TIME.asc(), PRACTICE_LAPS.ID.asc());
    }

    /** The highest run saved for the transponder in the session, 0 when it has no laps there. */
    public int highestRunNumber(Long practiceSessionId, String transponderNumber) {
        Integer highest = dsl.select(DSL.max(PRACTICE_LAPS.RUN_NUMBER)).from(PRACTICE_LAPS)
                .where(PRACTICE_LAPS.PRACTICE_SESSION_ID.eq(practiceSessionId))
                .and(PRACTICE_LAPS.TRANSPONDER_NUMBER.eq(transponderNumber))
                .fetchOne(0, Integer.class);
        return highest == null ? 0 : highest;
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(PRACTICE_LAPS.CREATED_AT);
    }

    @Override
    protected PracticeLap toEntity(PracticeLapsRecord r) {
        PracticeLap l = new PracticeLap();
        l.setId(r.getId());
        l.setPracticeSessionId(r.getPracticeSessionId());
        l.setTransponderNumber(r.getTransponderNumber());
        l.setUserId(r.getUserId());
        l.setLapNumber(r.getLapNumber());
        l.setRunNumber(r.getRunNumber());
        l.setLapTimeMs(r.getLapTimeMs());
        l.setCrossingTime(r.getCrossingTime());
        l.setCreatedAt(r.getCreatedAt());
        return l;
    }

    @Override
    protected void toRecord(PracticeLap l, PracticeLapsRecord r) {
        r.setPracticeSessionId(l.getPracticeSessionId());
        r.setTransponderNumber(l.getTransponderNumber());
        r.setUserId(l.getUserId());
        r.setLapNumber(l.getLapNumber());
        r.setRunNumber(l.getRunNumber());
        r.setLapTimeMs(l.getLapTimeMs());
        r.setCrossingTime(l.getCrossingTime());
        r.setCreatedAt(l.getCreatedAt());
    }

    @Override
    protected Long idOf(PracticeLap l) {
        return l.getId();
    }

    @Override
    protected void setId(PracticeLap l, Long id) {
        l.setId(id);
    }
}
