package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ScoringSystemsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ScoringSystems.SCORING_SYSTEMS;

/** Repository for {@link ScoringSystem} ({@code scoring_systems}); the config is converted with {@link ScoringConfigConverter}. */
@Repository
public class ScoringSystemRepository extends JooqRepository<ScoringSystem, ScoringSystemsRecord> {

    private static final ScoringConfigConverter CONFIG = new ScoringConfigConverter();

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
        ScoringSystem s = new ScoringSystem();
        s.setId(r.getId());
        s.setKind(ScoringKind.valueOf(r.getKind()));
        s.setName(r.getName());
        s.setBuiltIn(Boolean.TRUE.equals(r.getBuiltIn()));
        s.setConfig(CONFIG.convertToEntityAttribute(r.getConfig()));
        s.setCreatedAt(r.getCreatedAt());
        s.setUpdatedAt(r.getUpdatedAt());
        return s;
    }

    @Override
    protected void toRecord(ScoringSystem s, ScoringSystemsRecord r) {
        r.setKind(s.getKind().name());
        r.setName(s.getName());
        r.setBuiltIn(s.isBuiltIn());
        r.setConfig(CONFIG.convertToDatabaseColumn(s.getConfig()));
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
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
