package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.CompetitorMediaRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorMedia.COMPETITOR_MEDIA;

/** Repository for {@link DriverMedia} ({@code competitor_media}). */
@Repository
public class DriverMediaRepository extends JooqRepository<DriverMedia, CompetitorMediaRecord> {

    private static final DriverMediaMapper MAPPER = Mappers.getMapper(DriverMediaMapper.class);

    public DriverMediaRepository(DSLContext dsl) {
        super(dsl, COMPETITOR_MEDIA, COMPETITOR_MEDIA.ID);
    }

    /** The competitor's media, if they have any. */
    public Optional<DriverMedia> findByCompetitorId(Long competitorId) {
        return findOne(COMPETITOR_MEDIA.COMPETITOR_ID.eq(competitorId));
    }

    /** The media of these competitors. */
    public List<DriverMedia> findByCompetitorIds(Collection<Long> competitorIds) {
        return competitorIds.isEmpty() ? List.of() : findWhere(COMPETITOR_MEDIA.COMPETITOR_ID.in(competitorIds));
    }

    @Override
    protected DriverMedia toEntity(CompetitorMediaRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(DriverMedia m, CompetitorMediaRecord r) {
        MAPPER.toRecord(m, r);
    }

    @Override
    protected Long idOf(DriverMedia m) {
        return m.getId();
    }

    @Override
    protected void setId(DriverMedia m, Long id) {
        m.setId(id);
    }

    @Override
    protected String entityName() {
        return "Driver media";
    }
}
