package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacePointPenaltiesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacePointPenalties.RACE_POINT_PENALTIES;

/** Repository for {@link RacePointPenalty} ({@code race_point_penalties}). */
@Repository
public class RacePointPenaltyRepository extends JooqRepository<RacePointPenalty, RacePointPenaltiesRecord> {

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
        RacePointPenalty p = new RacePointPenalty();
        p.setId(r.getId());
        p.setRaceId(r.getRaceId());
        p.setEntryId(r.getEntryId());
        p.setPoints(r.getPoints());
        p.setReason(r.getReason());
        p.setAppliedBy(r.getAppliedBy());
        p.setAppliedAt(r.getAppliedAt());
        return p;
    }

    @Override
    protected void toRecord(RacePointPenalty p, RacePointPenaltiesRecord r) {
        r.setRaceId(p.getRaceId());
        r.setEntryId(p.getEntryId());
        r.setPoints(p.getPoints());
        r.setReason(p.getReason());
        r.setAppliedBy(p.getAppliedBy());
        r.setAppliedAt(p.getAppliedAt());
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
