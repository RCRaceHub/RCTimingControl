package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalAbsencesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAbsences.MARSHAL_ABSENCES;

/** Repository for {@link MarshalAbsence} ({@code marshal_absences}). */
@Repository
public class MarshalAbsenceRepository extends JooqRepository<MarshalAbsence, MarshalAbsencesRecord> {

    public MarshalAbsenceRepository(DSLContext dsl) {
        super(dsl, MARSHAL_ABSENCES, MARSHAL_ABSENCES.ID);
    }

    public List<MarshalAbsence> findByEventId(Long eventId) {
        return findWhere(MARSHAL_ABSENCES.EVENT_ID.eq(eventId));
    }

    @Override
    protected MarshalAbsence toEntity(MarshalAbsencesRecord r) {
        MarshalAbsence m = new MarshalAbsence();
        m.setId(r.getId());
        m.setRaceId(r.getRaceId());
        m.setEntryId(r.getEntryId());
        m.setEventId(r.getEventId());
        m.setRecordedAt(r.getRecordedAt());
        m.setRecordedBy(r.getRecordedBy());
        return m;
    }

    @Override
    protected void toRecord(MarshalAbsence m, MarshalAbsencesRecord r) {
        r.setRaceId(m.getRaceId());
        r.setEntryId(m.getEntryId());
        r.setEventId(m.getEventId());
        r.setRecordedAt(m.getRecordedAt());
        r.setRecordedBy(m.getRecordedBy());
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
