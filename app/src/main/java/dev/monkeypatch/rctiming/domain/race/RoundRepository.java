package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RoundsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/** Repository for {@link Round} ({@code rounds}). */
@Repository
public class RoundRepository extends JooqRepository<Round, RoundsRecord> {

    private static final RoundMapper MAPPER = Mappers.getMapper(RoundMapper.class);

    public RoundRepository(DSLContext dsl) {
        super(dsl, ROUNDS, ROUNDS.ID);
    }

    public List<Round> findByEventIdOrderBySequenceInEvent(Long eventId) {
        return findWhere(ROUNDS.EVENT_ID.eq(eventId), ROUNDS.SEQUENCE_IN_EVENT.asc(), ROUNDS.ID.asc());
    }

    public boolean existsByEventId(Long eventId) {
        return dsl.fetchExists(ROUNDS, ROUNDS.EVENT_ID.eq(eventId));
    }

    @Override
    protected Round toEntity(RoundsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Round round, RoundsRecord r) {
        MAPPER.toRecord(round, r);
    }

    @Override
    protected Long idOf(Round round) {
        return round.getId();
    }

    @Override
    protected void setId(Round round, Long id) {
        round.setId(id);
    }
}
