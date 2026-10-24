package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipEventLinksRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;

/** Repository for {@link ChampionshipEventLink} ({@code championship_event_links}). */
@Repository
public class ChampionshipEventLinkRepository extends JooqRepository<ChampionshipEventLink, ChampionshipEventLinksRecord> {

    private static final ChampionshipEventLinkMapper MAPPER = Mappers.getMapper(ChampionshipEventLinkMapper.class);

    public ChampionshipEventLinkRepository(DSLContext dsl) {
        super(dsl, CHAMPIONSHIP_EVENT_LINKS, CHAMPIONSHIP_EVENT_LINKS.ID);
    }

    public List<ChampionshipEventLink> findByChampionshipIdOrderByRoundNumberAsc(Long championshipId) {
        return findWhere(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId),
                CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER.asc(), CHAMPIONSHIP_EVENT_LINKS.ID.asc());
    }

    public boolean existsByChampionshipIdAndRoundNumber(Long championshipId, int roundNumber) {
        return dsl.fetchExists(CHAMPIONSHIP_EVENT_LINKS, CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER.eq(roundNumber)));
    }

    public boolean existsByChampionshipIdAndEventId(Long championshipId, Long eventId) {
        return dsl.fetchExists(CHAMPIONSHIP_EVENT_LINKS, CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.eq(eventId)));
    }

    @Transactional
    public void deleteByChampionshipIdAndEventId(Long championshipId, Long eventId) {
        dsl.deleteFrom(CHAMPIONSHIP_EVENT_LINKS).where(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.eq(eventId))).execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(CHAMPIONSHIP_EVENT_LINKS.CREATED_AT);
    }

    @Override
    protected ChampionshipEventLink toEntity(ChampionshipEventLinksRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ChampionshipEventLink c, ChampionshipEventLinksRecord r) {
        MAPPER.toRecord(c, r);
    }

    @Override
    protected Long idOf(ChampionshipEventLink c) {
        return c.getId();
    }

    @Override
    protected void setId(ChampionshipEventLink c, Long id) {
        c.setId(id);
    }
}
