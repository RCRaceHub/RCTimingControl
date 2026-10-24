package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacePointPenaltiesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacePointPenalties.RACE_POINT_PENALTIES;

/** Repository for {@link RacePointPenalty} ({@code race_point_penalties}). */
@Repository
public class RacePointPenaltyRepository extends JooqRepository<RacePointPenalty, RacePointPenaltiesRecord> {

    private static final RacePointPenaltyMapper MAPPER = Mappers.getMapper(RacePointPenaltyMapper.class);

    public RacePointPenaltyRepository(DSLContext dsl) {
        super(dsl, RACE_POINT_PENALTIES, RACE_POINT_PENALTIES.ID);
    }

    /** Every penalty given in the race, oldest first. */
    public List<RacePointPenalty> findByRaceId(Long raceId) {
        return findWhere(RACE_POINT_PENALTIES.RACE_ID.eq(raceId));
    }

    /** Removes the race's penalties, such as when the race is restarted to run again. */
    public void deleteByRaceId(Long raceId) {
        dsl.deleteFrom(RACE_POINT_PENALTIES).where(RACE_POINT_PENALTIES.RACE_ID.eq(raceId)).execute();
    }

    @Override
    protected RacePointPenalty toEntity(RacePointPenaltiesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RacePointPenalty p, RacePointPenaltiesRecord r) {
        MAPPER.toRecord(p, r);
    }

    @Override
    protected Long idOf(RacePointPenalty p) {
        return p.getId();
    }

    @Override
    protected void setId(RacePointPenalty p, Long id) {
        p.setId(id);
    }
}
