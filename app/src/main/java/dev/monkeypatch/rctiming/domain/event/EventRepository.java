package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;

/** Repository for {@link Event} ({@code events}). */
@Repository
public class EventRepository extends JooqRepository<Event, EventsRecord> {

    private static final EventMapper MAPPER = Mappers.getMapper(EventMapper.class);

    public EventRepository(DSLContext dsl) {
        super(dsl, EVENTS, EVENTS.ID);
    }

    /**
     * Loads an event to change something checked across the whole event (L11). Call it inside the
     * transaction that makes the change; {@link JooqRepository} says why no row lock is needed.
     */
    public Optional<Event> findByIdForUpdate(Long id) {
        return findById(id);
    }

    /** Events whose results are waiting to be queued for RaceHub (#27). */
    public List<Long> findIdsWithResultsExportPending() {
        return dsl.select(EVENTS.ID)
                .from(EVENTS)
                .where(EVENTS.RESULTS_EXPORT_PENDING.isNotNull())
                .orderBy(EVENTS.ID)
                .fetch(EVENTS.ID);
    }

    @Override
    protected Event toEntity(EventsRecord r) {
        Event e = MAPPER.toEntity(r);
        e.setResultsExportRevision(r.getResultsExportRevision());
        return e;
    }

    @Override
    protected void toRecord(Event e, EventsRecord r) {
        MAPPER.toRecord(e, r);
    }

    @Override
    protected Long idOf(Event e) {
        return e.getId();
    }

    @Override
    protected void setId(Event e, Long id) {
        e.setId(id);
    }
}
