package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ScoringSystemsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ScoringSystems.SCORING_SYSTEMS;

/** Repository for {@link ScoringSystem} ({@code scoring_systems}); the config is converted with {@link ScoringConfigConverter}. */
@Repository
public class ScoringSystemRepository extends JooqRepository<ScoringSystem, ScoringSystemsRecord> {

    static final ScoringConfigConverter CONFIG = new ScoringConfigConverter();

    private static final ScoringSystemMapper MAPPER = Mappers.getMapper(ScoringSystemMapper.class);

    public ScoringSystemRepository(DSLContext dsl) {
        super(dsl, SCORING_SYSTEMS, SCORING_SYSTEMS.ID);
    }

    /** The systems of one kind, built-ins first, then by name. */
    public List<ScoringSystem> findByKind(ScoringKind kind) {
        return dsl.selectFrom(SCORING_SYSTEMS)
                .where(SCORING_SYSTEMS.KIND.eq(kind.name()))
                .orderBy(SCORING_SYSTEMS.BUILT_IN.desc(), SCORING_SYSTEMS.NAME)
                .fetch(this::toEntity);
    }

    @Override
    protected ScoringSystem toEntity(ScoringSystemsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ScoringSystem s, ScoringSystemsRecord r) {
        MAPPER.toRecord(s, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(SCORING_SYSTEMS.CREATED_AT);
    }

    @Override
    protected Long idOf(ScoringSystem s) {
        return s.getId();
    }

    @Override
    protected void setId(ScoringSystem s, Long id) {
        s.setId(id);
    }
}
