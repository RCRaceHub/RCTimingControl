package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PracticeDriversRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.PracticeDrivers.PRACTICE_DRIVERS;

/** Repository for {@link PracticeDriver} ({@code practice_drivers}). */
@Repository
public class PracticeDriverRepository extends JooqRepository<PracticeDriver, PracticeDriversRecord> {

    private static final PracticeDriverMapper MAPPER = Mappers.getMapper(PracticeDriverMapper.class);

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

    /**
     * Moves the transponders named as one competitor to another, as a merge does (#123). A transponder is named once
     * per session, so no two rows can end up the same.
     */
    @Transactional
    public void moveCompetitor(Long fromCompetitorId, Long toCompetitorId) {
        dsl.update(PRACTICE_DRIVERS)
                .set(PRACTICE_DRIVERS.COMPETITOR_ID, toCompetitorId)
                .where(PRACTICE_DRIVERS.COMPETITOR_ID.eq(fromCompetitorId))
                .execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(PRACTICE_DRIVERS.CREATED_AT);
    }

    @Override
    protected PracticeDriver toEntity(PracticeDriversRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(PracticeDriver d, PracticeDriversRecord r) {
        MAPPER.toRecord(d, r);
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
