package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubFormatLinksRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacehubFormatLinks.RACEHUB_FORMAT_LINKS;

/** Repository for {@link RaceHubFormatLink} ({@code racehub_format_links}). */
@Repository
public class RaceHubFormatLinkRepository extends JooqRepository<RaceHubFormatLink, RacehubFormatLinksRecord> {

    private static final RaceHubFormatLinkMapper MAPPER = Mappers.getMapper(RaceHubFormatLinkMapper.class);

    public RaceHubFormatLinkRepository(DSLContext dsl) {
        super(dsl, RACEHUB_FORMAT_LINKS, RACEHUB_FORMAT_LINKS.ID);
    }

    @Override
    protected String entityName() {
        return "RaceHub format link";
    }

    /** The club's link for this format key, if one was made. */
    public Optional<RaceHubFormatLink> findByFormatKey(String formatKey) {
        return findWhere(RACEHUB_FORMAT_LINKS.FORMAT_KEY.eq(formatKey), RACEHUB_FORMAT_LINKS.ID.asc())
                .stream().findFirst();
    }

    /** Every link, ordered by format key. */
    public List<RaceHubFormatLink> findAllOrderByFormatKey() {
        return findWhere(DSL.trueCondition(), RACEHUB_FORMAT_LINKS.FORMAT_KEY.asc());
    }

    @Override
    protected RaceHubFormatLink toEntity(RacehubFormatLinksRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RaceHubFormatLink l, RacehubFormatLinksRecord r) {
        MAPPER.toRecord(l, r);
    }

    @Override
    protected Long idOf(RaceHubFormatLink l) {
        return l.getId();
    }

    @Override
    protected void setId(RaceHubFormatLink l, Long id) {
        l.setId(id);
    }
}
