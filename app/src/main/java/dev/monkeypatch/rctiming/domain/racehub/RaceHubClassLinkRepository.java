package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubClassLinksRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacehubClassLinks.RACEHUB_CLASS_LINKS;

/** Repository for {@link RaceHubClassLink} ({@code racehub_class_links}). */
@Repository
public class RaceHubClassLinkRepository extends JooqRepository<RaceHubClassLink, RacehubClassLinksRecord> {

    private static final RaceHubClassLinkMapper MAPPER = Mappers.getMapper(RaceHubClassLinkMapper.class);

    public RaceHubClassLinkRepository(DSLContext dsl) {
        super(dsl, RACEHUB_CLASS_LINKS, RACEHUB_CLASS_LINKS.ID);
    }

    @Override
    protected String entityName() {
        return "RaceHub class link";
    }

    /** The club's link for this class key, if one was made. */
    public Optional<RaceHubClassLink> findByClassKey(String classKey) {
        return findWhere(RACEHUB_CLASS_LINKS.CLASS_KEY.eq(classKey), RACEHUB_CLASS_LINKS.ID.asc())
                .stream().findFirst();
    }

    /** Every link, ordered by class key. */
    public List<RaceHubClassLink> findAllOrderByClassKey() {
        return findWhere(org.jooq.impl.DSL.trueCondition(), RACEHUB_CLASS_LINKS.CLASS_KEY.asc());
    }

    @Override
    protected RaceHubClassLink toEntity(RacehubClassLinksRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RaceHubClassLink l, RacehubClassLinksRecord r) {
        MAPPER.toRecord(l, r);
    }

    @Override
    protected Long idOf(RaceHubClassLink l) {
        return l.getId();
    }

    @Override
    protected void setId(RaceHubClassLink l, Long id) {
        l.setId(id);
    }
}
