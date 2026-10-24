package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipClassesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipClasses.CHAMPIONSHIP_CLASSES;

/** Repository for {@link ChampionshipClass} ({@code championship_classes}). */
@Repository
public class ChampionshipClassRepository extends JooqRepository<ChampionshipClass, ChampionshipClassesRecord> {

    private static final ChampionshipClassMapper MAPPER = Mappers.getMapper(ChampionshipClassMapper.class);

    public ChampionshipClassRepository(DSLContext dsl) {
        super(dsl, CHAMPIONSHIP_CLASSES, CHAMPIONSHIP_CLASSES.ID);
    }

    public List<ChampionshipClass> findByChampionshipId(Long championshipId) {
        return findWhere(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId));
    }

    public boolean existsByChampionshipIdAndRacingClassId(Long championshipId, Long racingClassId) {
        return dsl.fetchExists(CHAMPIONSHIP_CLASSES, CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID.eq(racingClassId)));
    }

    @Transactional
    public void deleteByChampionshipIdAndRacingClassId(Long championshipId, Long racingClassId) {
        dsl.deleteFrom(CHAMPIONSHIP_CLASSES).where(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID.eq(racingClassId))).execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(CHAMPIONSHIP_CLASSES.CREATED_AT);
    }

    @Override
    protected ChampionshipClass toEntity(ChampionshipClassesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ChampionshipClass c, ChampionshipClassesRecord r) {
        MAPPER.toRecord(c, r);
    }

    @Override
    protected Long idOf(ChampionshipClass c) {
        return c.getId();
    }

    @Override
    protected void setId(ChampionshipClass c, Long id) {
        c.setId(id);
    }
}
