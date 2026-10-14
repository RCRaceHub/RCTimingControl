package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeDriversRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeDrivers.PRACTICE_DRIVERS;

@Repository
public class PracticeDriverRepository extends JooqRepository<PracticeDriver, PracticeDriversRecord> {

    public PracticeDriverRepository(DSLContext dsl) {
        super(dsl, PRACTICE_DRIVERS, PRACTICE_DRIVERS.ID);
    }

    public List<PracticeDriver> findByPracticeSessionId(Long practiceSessionId) {
        return findWhere(PRACTICE_DRIVERS.PRACTICE_SESSION_ID.eq(practiceSessionId), PRACTICE_DRIVERS.ID.asc());
    }

    public Optional<PracticeDriver> findByPracticeSessionIdAndTransponderNumber(
            Long practiceSessionId, String transponderNumber) {
        return findWhere(PRACTICE_DRIVERS.PRACTICE_SESSION_ID.eq(practiceSessionId)
                .and(PRACTICE_DRIVERS.TRANSPONDER_NUMBER.eq(transponderNumber))).stream().findFirst();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(PRACTICE_DRIVERS.CREATED_AT);
    }

    @Override
    protected PracticeDriver toEntity(PracticeDriversRecord r) {
        PracticeDriver d = new PracticeDriver();
        d.setId(r.getId());
        d.setPracticeSessionId(r.getPracticeSessionId());
        d.setTransponderNumber(r.getTransponderNumber());
        d.setCompetitorId(r.getCompetitorId());
        d.setCreatedAt(r.getCreatedAt());
        return d;
    }

    @Override
    protected void toRecord(PracticeDriver d, PracticeDriversRecord r) {
        r.setPracticeSessionId(d.getPracticeSessionId());
        r.setTransponderNumber(d.getTransponderNumber());
        r.setCompetitorId(d.getCompetitorId());
        r.setCreatedAt(d.getCreatedAt());
    }

    @Override
    protected Long idOf(PracticeDriver d) {
        return d.getId();
    }

    @Override
    protected void setId(PracticeDriver d, Long id) {
        d.setId(id);
    }
}
