package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAbsencesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAbsences.MARSHAL_ABSENCES;

/** Repository for {@link MarshalAbsence} ({@code marshal_absences}). */
@Repository
public class MarshalAbsenceRepository extends JooqRepository<MarshalAbsence, MarshalAbsencesRecord> {

    private static final MarshalAbsenceMapper MAPPER = Mappers.getMapper(MarshalAbsenceMapper.class);

    public MarshalAbsenceRepository(DSLContext dsl) {
        super(dsl, MARSHAL_ABSENCES, MARSHAL_ABSENCES.ID);
    }

    public List<MarshalAbsence> findByEventId(Long eventId) {
        return findWhere(MARSHAL_ABSENCES.EVENT_ID.eq(eventId));
    }

    @Override
    protected MarshalAbsence toEntity(MarshalAbsencesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(MarshalAbsence m, MarshalAbsencesRecord r) {
        MAPPER.toRecord(m, r);
    }

    @Override
    protected Long idOf(MarshalAbsence m) {
        return m.getId();
    }

    @Override
    protected void setId(MarshalAbsence m, Long id) {
        m.setId(id);
    }
}
