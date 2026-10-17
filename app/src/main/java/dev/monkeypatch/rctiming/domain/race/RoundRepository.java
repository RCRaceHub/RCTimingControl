package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RoundsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/** Repository for {@link Round} ({@code rounds}). */
@Repository
public class RoundRepository extends JooqRepository<Round, RoundsRecord> {

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
        Round round = new Round();
        round.setId(r.getId());
        round.setEventId(r.getEventId());
        round.setType(r.getType() == null ? null : RoundType.valueOf(r.getType()));
        round.setRoundNumber(r.getRoundNumber());
        round.setSequenceInEvent(r.getSequenceInEvent());
        round.setStatus(r.getStatus() == null ? null : RoundStatus.valueOf(r.getStatus()));
        round.setCreatedAt(r.getCreatedAt());
        round.setUpdatedAt(r.getUpdatedAt());
        return round;
    }

    @Override
    protected void toRecord(Round round, RoundsRecord r) {
        r.setEventId(round.getEventId());
        r.setType(round.getType() == null ? null : round.getType().name());
        r.setRoundNumber(round.getRoundNumber());
        r.setSequenceInEvent(round.getSequenceInEvent());
        r.setStatus(round.getStatus() == null ? null : round.getStatus().name());
        r.setCreatedAt(round.getCreatedAt());
        r.setUpdatedAt(round.getUpdatedAt());
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
