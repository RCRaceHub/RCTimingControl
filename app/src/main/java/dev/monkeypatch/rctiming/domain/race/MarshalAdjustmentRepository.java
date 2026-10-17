package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAdjustmentsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAdjustments.MARSHAL_ADJUSTMENTS;

/** Repository for {@link MarshalAdjustment} ({@code marshal_adjustments}). */
@Repository
public class MarshalAdjustmentRepository extends JooqRepository<MarshalAdjustment, MarshalAdjustmentsRecord> {

    public MarshalAdjustmentRepository(DSLContext dsl) {
        super(dsl, MARSHAL_ADJUSTMENTS, MARSHAL_ADJUSTMENTS.ID);
    }

    public List<MarshalAdjustment> findByRaceIdOrderByAdjustedAt(Long raceId) {
        return findWhere(MARSHAL_ADJUSTMENTS.RACE_ID.eq(raceId), MARSHAL_ADJUSTMENTS.ADJUSTED_AT.asc(), MARSHAL_ADJUSTMENTS.ID.asc());
    }

    @Override
    protected MarshalAdjustment toEntity(MarshalAdjustmentsRecord r) {
        MarshalAdjustment m = new MarshalAdjustment();
        m.setId(r.getId());
        m.setRaceId(r.getRaceId());
        m.setEntryId(r.getEntryId());
        m.setTransponderNumber(r.getTransponderNumber());
        m.setLapDelta(r.getLapDelta());
        m.setRaceStateAtTime(r.getRaceStateAtTime());
        m.setActingUserId(r.getActingUserId());
        m.setActingUserName(r.getActingUserName());
        m.setAdjustedAt(r.getAdjustedAt());
        return m;
    }

    @Override
    protected void toRecord(MarshalAdjustment m, MarshalAdjustmentsRecord r) {
        r.setRaceId(m.getRaceId());
        r.setEntryId(m.getEntryId());
        r.setTransponderNumber(m.getTransponderNumber());
        r.setLapDelta(m.getLapDelta());
        r.setRaceStateAtTime(m.getRaceStateAtTime());
        r.setActingUserId(m.getActingUserId());
        r.setActingUserName(m.getActingUserName());
        r.setAdjustedAt(m.getAdjustedAt());
    }

    @Override
    protected Long idOf(MarshalAdjustment m) {
        return m.getId();
    }

    @Override
    protected void setId(MarshalAdjustment m, Long id) {
        m.setId(id);
    }
}
