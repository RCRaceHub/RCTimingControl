package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/** Repository for {@link Race} ({@code races}). */
@Repository
public class RaceRepository extends JooqRepository<Race, RacesRecord> {

    private static final RaceMapper MAPPER = Mappers.getMapper(RaceMapper.class);

    public RaceRepository(DSLContext dsl) {
        super(dsl, RACES, RACES.ID);
    }

    /** A round's races in running order. */
    public List<Race> findByRoundIdOrderBySequenceInRound(Long roundId) {
        return findWhere(RACES.ROUND_ID.eq(roundId), RACES.SEQUENCE_IN_ROUND.asc(), RACES.ID.asc());
    }

    /** Tables with a foreign key to a race and no cascade, which a delete of the race can't pass. */
    private static final List<String> RACE_RECORD_TABLES = List.of(
            "marshal_adjustments", "marshal_absences", "incident_reports", "penalties", "unknown_transponder_link");

    /**
     * Whether any race of the event has marshal laps, absences, incidents, penalties or transponder links against
     * it, as a race put back to pending by a restart or one with a penalty raised early can: records that
     * deleting the event's run order would destroy, or fail on.
     */
    public boolean hasRecordsAgainstEventRaces(Long eventId) {
        Field<Long> raceId = DSL.field(DSL.name("race_id"), Long.class);
        Select<Record1<Long>> eventRaces = dsl.select(RACES.ID).from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId));
        return RACE_RECORD_TABLES.stream().anyMatch(table ->
                dsl.fetchExists(DSL.table(DSL.name(table)), raceId.in(eventRaces)));
    }

    /**
     * Whether the race has marshal laps, absences, incidents, penalties or transponder links against it, which
     * deleting the race would destroy or fail on (a foreign key without a cascade).
     */
    public boolean hasRecordsAgainstRace(Long raceId) {
        Field<Long> column = DSL.field(DSL.name("race_id"), Long.class);
        return RACE_RECORD_TABLES.stream().anyMatch(table ->
                dsl.fetchExists(DSL.table(DSL.name(table)), column.eq(raceId)));
    }

    /** Whether any race, of any round and status, belongs to the event class. */
    public boolean existsByEventClassId(Long eventClassId) {
        return dsl.fetchExists(RACES, RACES.EVENT_CLASS_ID.eq(eventClassId));
    }

    /**
     * Puts the event's run order back to the generated one by clearing every race's {@code run_rank}; for the
     * services that add or remove races of an event whose finals were reordered. Runs in the caller's transaction.
     */
    public void clearRunRanks(Long eventId) {
        dsl.update(RACES).setNull(RACES.RUN_RANK)
                .where(RACES.RUN_RANK.isNotNull())
                .and(RACES.ROUND_ID.in(DSL.select(ROUNDS.ID).from(ROUNDS).where(ROUNDS.EVENT_ID.eq(eventId))))
                .execute();
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
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Race race, RacesRecord r) {
        MAPPER.toRecord(race, r);
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
