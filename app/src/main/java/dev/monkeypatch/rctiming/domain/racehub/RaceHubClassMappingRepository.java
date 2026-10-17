package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RacehubClassMappingsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
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
        RaceHubClassMapping m = new RaceHubClassMapping();
        m.setId(r.getId());
        m.setEventId(r.getEventId());
        m.setRacehubEventClassId(r.getRacehubEventClassId());
        m.setEventClassId(r.getEventClassId());
        m.setCreatedAt(r.getCreatedAt());
        m.setUpdatedAt(r.getUpdatedAt());
        return m;
    }

    @Override
    protected void toRecord(RaceHubClassMapping m, RacehubClassMappingsRecord r) {
        r.setEventId(m.getEventId());
        r.setRacehubEventClassId(m.getRacehubEventClassId());
        r.setEventClassId(m.getEventClassId());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
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
