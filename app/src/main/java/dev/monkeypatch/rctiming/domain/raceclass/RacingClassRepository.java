package dev.monkeypatch.rctiming.domain.raceclass;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacingClassesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;

/** Repository for {@link RacingClass} ({@code racing_classes}). */
@Repository
public class RacingClassRepository extends JooqRepository<RacingClass, RacingClassesRecord> {

    private static final RacingClassMapper MAPPER = Mappers.getMapper(RacingClassMapper.class);

    public RacingClassRepository(DSLContext dsl) {
        super(dsl, RACING_CLASSES, RACING_CLASSES.ID);
    }

    @Override
    protected RacingClass toEntity(RacingClassesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RacingClass c, RacingClassesRecord r) {
        MAPPER.toRecord(c, r);
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
