package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeLapsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeLaps.PRACTICE_LAPS;

/** Repository for {@link PracticeLap} ({@code practice_laps}); each finder orders its laps as its name says. */
@Repository
public class PracticeLapRepository extends JooqRepository<PracticeLap, PracticeLapsRecord> {

    private static final PracticeLapMapper MAPPER = Mappers.getMapper(PracticeLapMapper.class);

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
        PracticeLap e = MAPPER.toEntity(r);
        setId(e, r.getId());
        return e;
    }

    @Override
    protected void toRecord(PracticeLap l, PracticeLapsRecord r) {
        MAPPER.toRecord(l, r);
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
