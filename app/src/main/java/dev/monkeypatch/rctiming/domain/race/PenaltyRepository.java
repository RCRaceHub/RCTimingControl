package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PenaltiesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Penalties.PENALTIES;

/** Repository for {@link Penalty} ({@code penalties}). */
@Repository
public class PenaltyRepository extends JooqRepository<Penalty, PenaltiesRecord> {

    private static final PenaltyMapper MAPPER = Mappers.getMapper(PenaltyMapper.class);

    public PenaltyRepository(DSLContext dsl) {
        super(dsl, PENALTIES, PENALTIES.ID);
    }

    public List<Penalty> findByRaceId(Long raceId) {
        return findWhere(PENALTIES.RACE_ID.eq(raceId), PENALTIES.APPLIED_AT.asc(), PENALTIES.ID.asc());
    }

    /** Removes the race's lap and time penalties, such as when the race is restarted to run again. */
    public void deleteByRaceId(Long raceId) {
        dsl.deleteFrom(PENALTIES).where(PENALTIES.RACE_ID.eq(raceId)).execute();
    }

    @Override
    protected Penalty toEntity(PenaltiesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Penalty p, PenaltiesRecord r) {
        MAPPER.toRecord(p, r);
    }

    @Override
    protected Long idOf(Penalty p) {
        return p.getId();
    }

    @Override
    protected void setId(Penalty p, Long id) {
        p.setId(id);
    }
}
