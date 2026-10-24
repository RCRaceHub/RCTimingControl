package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Championships.CHAMPIONSHIPS;

/** Repository for {@link Championship} ({@code championships}). */
@Repository
public class ChampionshipRepository extends JooqRepository<Championship, ChampionshipsRecord> {

    private static final ChampionshipMapper MAPPER = Mappers.getMapper(ChampionshipMapper.class);

    public ChampionshipRepository(DSLContext dsl) {
        super(dsl, CHAMPIONSHIPS, CHAMPIONSHIPS.ID);
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(CHAMPIONSHIPS.CREATED_AT);
    }

    @Override
    protected Championship toEntity(ChampionshipsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Championship c, ChampionshipsRecord r) {
        MAPPER.toRecord(c, r);
    }

    @Override
    protected Long idOf(Championship c) {
        return c.getId();
    }

    @Override
    protected void setId(Championship c, Long id) {
        c.setId(id);
    }
}
