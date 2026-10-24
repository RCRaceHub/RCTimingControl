package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAdjustmentsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAdjustments.MARSHAL_ADJUSTMENTS;

/** Repository for {@link MarshalAdjustment} ({@code marshal_adjustments}). */
@Repository
public class MarshalAdjustmentRepository extends JooqRepository<MarshalAdjustment, MarshalAdjustmentsRecord> {

    private static final MarshalAdjustmentMapper MAPPER = Mappers.getMapper(MarshalAdjustmentMapper.class);

    public MarshalAdjustmentRepository(DSLContext dsl) {
        super(dsl, MARSHAL_ADJUSTMENTS, MARSHAL_ADJUSTMENTS.ID);
    }

    public List<MarshalAdjustment> findByRaceIdOrderByAdjustedAt(Long raceId) {
        return findWhere(MARSHAL_ADJUSTMENTS.RACE_ID.eq(raceId), MARSHAL_ADJUSTMENTS.ADJUSTED_AT.asc(), MARSHAL_ADJUSTMENTS.ID.asc());
    }

    @Override
    protected MarshalAdjustment toEntity(MarshalAdjustmentsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(MarshalAdjustment m, MarshalAdjustmentsRecord r) {
        MAPPER.toRecord(m, r);
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
