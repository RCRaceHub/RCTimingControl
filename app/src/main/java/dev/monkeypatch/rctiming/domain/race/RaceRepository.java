package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

@Repository
public class RaceRepository extends JooqRepository<Race, RacesRecord> {

    public RaceRepository(DSLContext dsl) {
        super(dsl, RACES, RACES.ID);
    }

    public List<Race> findByRoundIdOrderBySequenceInRound(Long roundId) {
        return findWhere(RACES.ROUND_ID.eq(roundId), RACES.SEQUENCE_IN_ROUND.asc(), RACES.ID.asc());
    }

    /** For BumpUpSeedingService: a class's races in rounds of the given type, such as its finals. */
    public List<Race> findByEventClassIdAndRoundType(Long eventClassId, RoundType roundType) {
        return dsl.select(RACES.fields()).from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(RACES.EVENT_CLASS_ID.eq(eventClassId).and(ROUNDS.TYPE.eq(roundType.name())))
                .orderBy(RACES.ID)
                .fetch(row -> toEntity(row.into(RACES)));
    }

    /** For BumpUpSeedingService: a class's race with the given final letter. */
    public List<Race> findByEventClassIdAndFinalLetter(Long eventClassId, String finalLetter) {
        return findWhere(RACES.EVENT_CLASS_ID.eq(eventClassId).and(RACES.FINAL_LETTER.eq(finalLetter)));
    }

    /**
     * For the decoder listener: the race currently in the given status, such as the running one.
     * Runs in a transaction so it reads through the write connection: it waits for a race being
     * started or finished to commit, rather than reading the old status from the read pool and
     * sending passings to the wrong race.
     */
    @Transactional(readOnly = true)
    public Optional<Race> findFirstByStatus(RaceStatus status) {
        return dsl.selectFrom(RACES).where(RACES.STATUS.eq(status.name())).orderBy(RACES.ID).limit(1)
                .fetchOptional().map(this::toEntity);
    }

    @Override
    protected Race toEntity(RacesRecord r) {
        Race race = new Race();
        race.setId(r.getId());
        race.setRoundId(r.getRoundId());
        race.setEventClassId(r.getEventClassId());
        race.setHeatNumber(r.getHeatNumber());
        race.setSequenceInRound(r.getSequenceInRound());
        race.setFinalLetter(r.getFinalLetter());
        race.setStartType(r.getStartType() == null ? null : StartType.valueOf(r.getStartType()));
        race.setStatus(r.getStatus() == null ? null : RaceStatus.valueOf(r.getStatus()));
        race.setStartedAt(r.getStartedAt());
        race.setFinishedAt(r.getFinishedAt());
        race.setAbandonedAt(r.getAbandonedAt());
        race.setBumpSlots(r.getBumpSlots() == null ? 0 : r.getBumpSlots());
        race.setCreatedAt(r.getCreatedAt());
        race.setUpdatedAt(r.getUpdatedAt());
        return race;
    }

    @Override
    protected void toRecord(Race race, RacesRecord r) {
        r.setRoundId(race.getRoundId());
        r.setEventClassId(race.getEventClassId());
        r.setHeatNumber(race.getHeatNumber());
        r.setSequenceInRound(race.getSequenceInRound());
        r.setFinalLetter(race.getFinalLetter());
        r.setStartType(race.getStartType() == null ? null : race.getStartType().name());
        r.setStatus(race.getStatus() == null ? null : race.getStatus().name());
        r.setStartedAt(race.getStartedAt());
        r.setFinishedAt(race.getFinishedAt());
        r.setAbandonedAt(race.getAbandonedAt());
        r.setBumpSlots(race.getBumpSlots());
        r.setCreatedAt(race.getCreatedAt());
        r.setUpdatedAt(race.getUpdatedAt());
    }

    @Override
    protected Long idOf(Race race) {
        return race.getId();
    }

    @Override
    protected void setId(Race race, Long id) {
        race.setId(id);
    }
}
