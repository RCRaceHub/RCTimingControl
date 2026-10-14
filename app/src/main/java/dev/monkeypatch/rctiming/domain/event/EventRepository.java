package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;

@Repository
public class EventRepository extends JooqRepository<Event, EventsRecord> {

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
        Event e = new Event();
        e.setId(r.getId());
        e.setName(r.getName());
        e.setEventDate(r.getEventDate());
        e.setStatus(r.getStatus() == null ? null : EventStatus.valueOf(r.getStatus()));
        e.setEntryOpensAt(r.getEntryOpensAt());
        e.setEntryClosesAt(r.getEntryClosesAt());
        e.setTrackId(r.getTrackId());
        e.setCreatedAt(r.getCreatedAt());
        e.setUpdatedAt(r.getUpdatedAt());
        e.setRacehubLastImportAt(r.getRacehubLastImportAt());
        e.setRacehubLastRevision(r.getRacehubLastRevision());
        e.setRacehubEventId(r.getRacehubEventId());
        e.setResultsExportRevision(r.getResultsExportRevision());
        e.setResultsExportPending(r.getResultsExportPending());
        e.setLiveFeedEnabled(r.getLiveFeedEnabled());
        return e;
    }

    @Override
    protected void toRecord(Event e, EventsRecord r) {
        r.setName(e.getName());
        r.setEventDate(e.getEventDate());
        r.setStatus(e.getStatus() == null ? null : e.getStatus().name());
        r.setEntryOpensAt(e.getEntryOpensAt());
        r.setEntryClosesAt(e.getEntryClosesAt());
        r.setTrackId(e.getTrackId());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setRacehubLastImportAt(e.getRacehubLastImportAt());
        r.setRacehubLastRevision(e.getRacehubLastRevision());
        r.setRacehubEventId(e.getRacehubEventId());
        r.setResultsExportRevision(e.getResultsExportRevision());
        r.setResultsExportPending(e.getResultsExportPending());
        r.setLiveFeedEnabled(e.isLiveFeedEnabled());
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
