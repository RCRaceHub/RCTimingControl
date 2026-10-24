package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalPenaltiesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalPenalties.MARSHAL_PENALTIES;

/** Repository for {@link MarshalPenalty} ({@code marshal_penalties}). */
@Repository
public class MarshalPenaltyRepository extends JooqRepository<MarshalPenalty, MarshalPenaltiesRecord> {

    private static final MarshalPenaltyMapper MAPPER = Mappers.getMapper(MarshalPenaltyMapper.class);

    public MarshalPenaltyRepository(DSLContext dsl) {
        super(dsl, MARSHAL_PENALTIES, MARSHAL_PENALTIES.ID);
    }

    @Override
    protected MarshalPenalty toEntity(MarshalPenaltiesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(MarshalPenalty m, MarshalPenaltiesRecord r) {
        MAPPER.toRecord(m, r);
    }

    public boolean existsByAbsenceId(Long absenceId) {
        return !findWhere(MARSHAL_PENALTIES.ABSENCE_ID.eq(absenceId)).isEmpty();
    }

    public List<MarshalPenalty> findByEventIdAndKind(Long eventId, MarshalPenaltyKind kind) {
        return findWhere(MARSHAL_PENALTIES.EVENT_ID.eq(eventId).and(MARSHAL_PENALTIES.KIND.eq(kind.name())));
    }

    @Override
    protected Long idOf(MarshalPenalty m) {
        return m.getId();
    }

    @Override
    protected void setId(MarshalPenalty m, Long id) {
        m.setId(id);
    }
}
