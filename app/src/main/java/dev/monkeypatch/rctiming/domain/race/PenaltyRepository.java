package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.PenaltiesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Penalties.PENALTIES;

/** Repository for {@link Penalty} ({@code penalties}). */
@Repository
public class PenaltyRepository extends JooqRepository<Penalty, PenaltiesRecord> {

    public PenaltyRepository(DSLContext dsl) {
        super(dsl, PENALTIES, PENALTIES.ID);
    }

    public List<Penalty> findByRaceId(Long raceId) {
        return findWhere(PENALTIES.RACE_ID.eq(raceId));
    }

    @Override
    protected Penalty toEntity(PenaltiesRecord r) {
        Penalty p = new Penalty();
        p.setId(r.getId());
        p.setRaceId(r.getRaceId());
        p.setEntryId(r.getEntryId());
        p.setPenaltyType(PenaltyType.valueOf(r.getPenaltyType()));
        p.setValue(r.getValue());
        p.setReason(r.getReason());
        p.setAppliedBy(r.getAppliedBy());
        p.setAppliedAt(r.getAppliedAt());
        return p;
    }

    @Override
    protected void toRecord(Penalty p, PenaltiesRecord r) {
        r.setRaceId(p.getRaceId());
        r.setEntryId(p.getEntryId());
        r.setPenaltyType(p.getPenaltyType().name());
        r.setValue(p.getValue());
        r.setReason(p.getReason());
        r.setAppliedBy(p.getAppliedBy());
        r.setAppliedAt(p.getAppliedAt());
    }

    @Override
    protected Long idOf(Penalty p) {
        return p.getId();
    }

    @Override
    protected void setId(Penalty p, Long id) {
        p.setId(id);
    }
}
