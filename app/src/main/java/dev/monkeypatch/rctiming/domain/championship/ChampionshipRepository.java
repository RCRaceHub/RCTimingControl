package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Championships.CHAMPIONSHIPS;

/** Repository for {@link Championship} ({@code championships}). */
@Repository
public class ChampionshipRepository extends JooqRepository<Championship, ChampionshipsRecord> {

    public ChampionshipRepository(DSLContext dsl) {
        super(dsl, CHAMPIONSHIPS, CHAMPIONSHIPS.ID);
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(CHAMPIONSHIPS.CREATED_AT);
    }

    @Override
    protected Championship toEntity(ChampionshipsRecord r) {
        Championship c = new Championship();
        c.setId(r.getId());
        c.setName(r.getName());
        c.setBestXFromYX(r.getBestXFromYX());
        c.setBestXFromYY(r.getBestXFromYY());
        c.setScoringSource(r.getScoringSource() == null ? null : ScoringSource.valueOf(r.getScoringSource()));
        c.setTqBonusPoints(r.getTqBonusPoints());
        c.setAfinalWinnerBonusPoints(r.getAfinalWinnerBonusPoints());
        c.setCreatedAt(r.getCreatedAt());
        c.setUpdatedAt(r.getUpdatedAt());
        return c;
    }

    @Override
    protected void toRecord(Championship c, ChampionshipsRecord r) {
        r.setName(c.getName());
        r.setBestXFromYX(c.getBestXFromYX());
        r.setBestXFromYY(c.getBestXFromYY());
        r.setScoringSource(c.getScoringSource() == null ? null : c.getScoringSource().name());
        r.setTqBonusPoints(c.getTqBonusPoints());
        r.setAfinalWinnerBonusPoints(c.getAfinalWinnerBonusPoints());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
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
