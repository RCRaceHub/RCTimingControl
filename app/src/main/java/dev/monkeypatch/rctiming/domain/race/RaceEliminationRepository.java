package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RaceEliminationsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEliminations.RACE_ELIMINATIONS;

/** Repository for {@link RaceElimination} ({@code race_eliminations}). */
@Repository
public class RaceEliminationRepository extends JooqRepository<RaceElimination, RaceEliminationsRecord> {

    public RaceEliminationRepository(DSLContext dsl) {
        super(dsl, RACE_ELIMINATIONS, RACE_ELIMINATIONS.ID);
    }

    /** Every row of the race, eliminations and revivals, in the order they were marked (row ids, not clock times). */
    public List<RaceElimination> findByRaceId(Long raceId) {
        return findWhere(RACE_ELIMINATIONS.RACE_ID.eq(raceId), RACE_ELIMINATIONS.ID.asc());
    }

    /** The entry's current elimination in the race, if it is dead now. */
    public Optional<RaceElimination> findActive(Long raceId, Long entryId) {
        return findOne(RACE_ELIMINATIONS.RACE_ID.eq(raceId).and(RACE_ELIMINATIONS.ENTRY_ID.eq(entryId))
                .and(RACE_ELIMINATIONS.REVIVED_AT.isNull()));
    }

    /** Removes the race's rows, such as when the race is restarted to run again. */
    public void deleteByRaceId(Long raceId) {
        dsl.deleteFrom(RACE_ELIMINATIONS).where(RACE_ELIMINATIONS.RACE_ID.eq(raceId)).execute();
    }

    @Override
    protected RaceElimination toEntity(RaceEliminationsRecord r) {
        RaceElimination e = new RaceElimination();
        e.setId(r.getId());
        e.setRaceId(r.getRaceId());
        e.setEntryId(r.getEntryId());
        e.setEliminatedAt(r.getEliminatedAt());
        e.setEliminatedBy(r.getEliminatedBy());
        e.setRevivedAt(r.getRevivedAt());
        e.setRevivedBy(r.getRevivedBy());
        return e;
    }

    @Override
    protected void toRecord(RaceElimination e, RaceEliminationsRecord r) {
        r.setRaceId(e.getRaceId());
        r.setEntryId(e.getEntryId());
        r.setEliminatedAt(e.getEliminatedAt());
        r.setEliminatedBy(e.getEliminatedBy());
        r.setRevivedAt(e.getRevivedAt());
        r.setRevivedBy(e.getRevivedBy());
    }

    @Override
    protected Long idOf(RaceElimination e) {
        return e.getId();
    }

    @Override
    protected void setId(RaceElimination e, Long id) {
        e.setId(id);
    }
}
