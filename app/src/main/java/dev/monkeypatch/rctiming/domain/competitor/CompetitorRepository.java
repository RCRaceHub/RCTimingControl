package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.CompetitorsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;

/**
 * Repository for {@link Competitor} ({@code competitors}). Name matching is done in Java with {@link
 * dev.monkeypatch.rctiming.domain.Names#matchKey}, not in SQL.
 */
@Repository
public class CompetitorRepository extends JooqRepository<Competitor, CompetitorsRecord> {

    private static final CompetitorMapper MAPPER = Mappers.getMapper(CompetitorMapper.class);

    public CompetitorRepository(DSLContext dsl) {
        super(dsl, COMPETITORS, COMPETITORS.ID);
    }

    /** The competitor an import has seen before: the same source and the same id in that source. */
    public Optional<Competitor> findByExternalSourceAndExternalId(String externalSource, String externalId) {
        return findOne(COMPETITORS.EXTERNAL_SOURCE.eq(externalSource).and(COMPETITORS.EXTERNAL_ID.eq(externalId)));
    }

    /** Competitors from any source with this BRCA number. */
    public List<Competitor> findByBrcaNumber(String brcaNumber) {
        return findWhere(COMPETITORS.BRCA_NUMBER.eq(brcaNumber));
    }

    /**
     * Competitors from any source with no BRCA number, by {@link Names#matchKey} of their name. Loaded once
     * for a whole import rather than once for each of its rows.
     */
    public Map<String, List<Competitor>> findWithoutBrcaNumberByMatchKey() {
        return findWhere(COMPETITORS.BRCA_NUMBER.isNull()).stream()
                .collect(Collectors.groupingBy(c -> Names.matchKey(c.getDisplayName())));
    }

    /**
     * Competitors from any source whose name is the same as this one ({@link Names#matchKey}), so "alex  rowe"
     * finds "Alex Rowe". The rule a typed walk-in name is checked against (#123). The names are compared in
     * Java: a club has hundreds of competitors, and this runs once per typed walk-in.
     */
    public List<Competitor> findBySameName(String displayName) {
        String target = Names.matchKey(displayName);
        return findAll().stream()
                .filter(c -> Names.matchKey(c.getDisplayName()).equals(target))
                .toList();
    }

    /** Every competitor whose public name is initials or hidden, by id (#265). */
    public Map<Long, NameVisibility> findNameVisibilityNotShow() {
        return dsl.select(COMPETITORS.ID, COMPETITORS.NAME_VISIBILITY).from(COMPETITORS)
                .where(COMPETITORS.NAME_VISIBILITY.ne(NameVisibility.SHOW.name()))
                .fetchMap(COMPETITORS.ID, r -> NameVisibility.valueOf(r.get(COMPETITORS.NAME_VISIBILITY)));
    }

    @Override
    protected Competitor toEntity(CompetitorsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Competitor c, CompetitorsRecord r) {
        MAPPER.toRecord(c, r);
    }

    @Override
    protected Long idOf(Competitor c) {
        return c.getId();
    }

    @Override
    protected void setId(Competitor c, Long id) {
        c.setId(id);
    }
}
