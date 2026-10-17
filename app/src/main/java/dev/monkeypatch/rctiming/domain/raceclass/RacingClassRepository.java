package dev.monkeypatch.rctiming.domain.raceclass;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacingClassesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;

/** Repository for {@link RacingClass} ({@code racing_classes}). */
@Repository
public class RacingClassRepository extends JooqRepository<RacingClass, RacingClassesRecord> {

    public RacingClassRepository(DSLContext dsl) {
        super(dsl, RACING_CLASSES, RACING_CLASSES.ID);
    }

    @Override
    protected RacingClass toEntity(RacingClassesRecord r) {
        RacingClass c = new RacingClass();
        c.setId(r.getId());
        c.setName(r.getName());
        c.setDescription(r.getDescription());
        c.setCreatedAt(r.getCreatedAt());
        c.setUpdatedAt(r.getUpdatedAt());
        return c;
    }

    @Override
    protected void toRecord(RacingClass c, RacingClassesRecord r) {
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
    }

    @Override
    protected Long idOf(RacingClass c) {
        return c.getId();
    }

    @Override
    protected void setId(RacingClass c, Long id) {
        c.setId(id);
    }
}
