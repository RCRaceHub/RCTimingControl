package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipClasses.CHAMPIONSHIP_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Championships.CHAMPIONSHIPS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;

/**
 * Reads a championship's name and class names for the standings sheet. Called on a request thread by
 * {@code PublicChampionshipController}; one snapshot on the read pool.
 */
@Component
@ReadTransaction
public class ChampionshipInfoQuery {

    private final DSLContext dsl;

    public ChampionshipInfoQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * The championship's name and its scored classes by name.
     *
     * @throws EntityNotFoundException if there is no such championship (HTTP 404)
     */
    public ChampionshipInfoDto find(long championshipId) {
        String name = dsl.select(CHAMPIONSHIPS.NAME)
                .from(CHAMPIONSHIPS)
                .where(CHAMPIONSHIPS.ID.eq(championshipId))
                .fetchOne(CHAMPIONSHIPS.NAME);
        if (name == null) {
            throw new EntityNotFoundException("No championship " + championshipId);
        }
        List<ChampionshipInfoDto.ClassInfo> classes = dsl
                .select(RACING_CLASSES.ID, RACING_CLASSES.NAME)
                .from(CHAMPIONSHIP_CLASSES)
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID))
                .where(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId))
                .orderBy(RACING_CLASSES.NAME.asc())
                .fetch(r -> new ChampionshipInfoDto.ClassInfo(r.get(RACING_CLASSES.ID), r.get(RACING_CLASSES.NAME)));
        return new ChampionshipInfoDto(championshipId, name, classes);
    }
}
