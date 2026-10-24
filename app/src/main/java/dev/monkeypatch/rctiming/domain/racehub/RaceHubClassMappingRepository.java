package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubClassMappingsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacehubClassMappings.RACEHUB_CLASS_MAPPINGS;

/**
 * Repository for {@link RaceHubClassMapping} ({@code racehub_class_mappings}); the CSV import uses it too, keyed {@code
 * CSV:<class name>}.
 */
@Repository
public class RaceHubClassMappingRepository extends JooqRepository<RaceHubClassMapping, RacehubClassMappingsRecord> {

    private static final RaceHubClassMappingMapper MAPPER = Mappers.getMapper(RaceHubClassMappingMapper.class);

    public RaceHubClassMappingRepository(DSLContext dsl) {
        super(dsl, RACEHUB_CLASS_MAPPINGS, RACEHUB_CLASS_MAPPINGS.ID);
    }

    @Override
    protected String entityName() {
        return "RaceHub class mapping";
    }

    public List<RaceHubClassMapping> findByEventIdOrderByRacehubEventClassId(Long eventId) {
        return findWhere(RACEHUB_CLASS_MAPPINGS.EVENT_ID.eq(eventId),
                RACEHUB_CLASS_MAPPINGS.RACEHUB_EVENT_CLASS_ID.asc());
    }

    @Transactional
    public void deleteByEventId(Long eventId) {
        dsl.deleteFrom(RACEHUB_CLASS_MAPPINGS).where(RACEHUB_CLASS_MAPPINGS.EVENT_ID.eq(eventId)).execute();
    }

    @Override
    protected RaceHubClassMapping toEntity(RacehubClassMappingsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RaceHubClassMapping m, RacehubClassMappingsRecord r) {
        MAPPER.toRecord(m, r);
    }

    @Override
    protected Long idOf(RaceHubClassMapping m) {
        return m.getId();
    }

    @Override
    protected void setId(RaceHubClassMapping m, Long id) {
        m.setId(id);
    }
}
