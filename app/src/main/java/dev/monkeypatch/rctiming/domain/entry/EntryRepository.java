package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntriesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;

@Repository
public class EntryRepository extends JooqRepository<Entry, EntriesRecord> {

    public EntryRepository(DSLContext dsl) {
        super(dsl, ENTRIES, ENTRIES.ID);
    }

    /** For the round generator: an event class's entries with the given status. */
    public List<Entry> findByEventClassIdAndStatus(Long eventClassId, EntryStatus status) {
        return findWhere(ENTRIES.EVENT_CLASS_ID.eq(eventClassId).and(ENTRIES.STATUS.eq(status.name())));
    }

    /** For the RaceHub import (L7): upsert by the source's entry id. */
    public Optional<Entry> findByExternalSourceAndExternalEntryId(String externalSource, String externalEntryId) {
        return findOne(ENTRIES.EXTERNAL_SOURCE.eq(externalSource).and(ENTRIES.EXTERNAL_ENTRY_ID.eq(externalEntryId)));
    }

    /** Every entry a competitor has, of any status and in any event. */
    public List<Entry> findByCompetitorId(Long competitorId) {
        return findWhere(ENTRIES.COMPETITOR_ID.eq(competitorId));
    }

    public List<Entry> findByEventId(Long eventId) {
        return findWhere(ENTRIES.EVENT_ID.eq(eventId));
    }

    /**
     * Loads an entry for check-in's read-then-write (L11). Call it inside the transaction that makes
     * the change; {@link JooqRepository} says why no row lock is needed.
     */
    public Optional<Entry> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    protected Entry toEntity(EntriesRecord r) {
        Entry e = new Entry();
        e.setId(r.getId());
        e.setUserId(r.getUserId());
        e.setCompetitorId(r.getCompetitorId());
        e.setEventId(r.getEventId());
        e.setEventClassId(r.getEventClassId());
        e.setTransponderNumberSnapshot(r.getTransponderNumber());
        e.setTransponderLabelSnapshot(r.getTransponderLabel());
        e.setSecondaryTransponderNumber(r.getSecondaryTransponderNumber());
        e.setImportedTransponderNumber(r.getImportedTransponderNumber());
        e.setImportedSecondaryTransponderNumber(r.getImportedSecondaryTransponderNumber());
        e.setStatus(r.getStatus() == null ? null : EntryStatus.valueOf(r.getStatus()));
        e.setSubmittedAt(r.getSubmittedAt());
        e.setConfirmedAt(r.getConfirmedAt());
        e.setWithdrawnAt(r.getWithdrawnAt());
        e.setUpdatedAt(r.getUpdatedAt());
        e.setExternalSource(r.getExternalSource());
        e.setExternalEntryId(r.getExternalEntryId());
        e.setExternalEntryVersion(r.getExternalEntryVersion());
        e.setRacehubArrival(r.getRacehubArrival());
        e.setRacehubEventClassId(r.getRacehubEventClassId());
        e.setCheckedInAt(r.getCheckedInAt());
        e.setCheckedInByUserId(r.getCheckedInByUserId());
        return e;
    }

    @Override
    protected void toRecord(Entry e, EntriesRecord r) {
        r.setUserId(e.getUserId());
        r.setCompetitorId(e.getCompetitorId());
        r.setEventId(e.getEventId());
        r.setEventClassId(e.getEventClassId());
        r.setTransponderNumber(e.getTransponderNumberSnapshot());
        r.setTransponderLabel(e.getTransponderLabelSnapshot());
        r.setSecondaryTransponderNumber(e.getSecondaryTransponderNumber());
        r.setImportedTransponderNumber(e.getImportedTransponderNumber());
        r.setImportedSecondaryTransponderNumber(e.getImportedSecondaryTransponderNumber());
        r.setStatus(e.getStatus() == null ? null : e.getStatus().name());
        r.setSubmittedAt(e.getSubmittedAt());
        r.setConfirmedAt(e.getConfirmedAt());
        r.setWithdrawnAt(e.getWithdrawnAt());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setExternalSource(e.getExternalSource());
        r.setExternalEntryId(e.getExternalEntryId());
        r.setExternalEntryVersion(e.getExternalEntryVersion());
        r.setRacehubArrival(e.getRacehubArrival());
        r.setRacehubEventClassId(e.getRacehubEventClassId());
        r.setCheckedInAt(e.getCheckedInAt());
        r.setCheckedInByUserId(e.getCheckedInByUserId());
    }

    @Override
    protected Long idOf(Entry e) {
        return e.getId();
    }

    @Override
    protected void setId(Entry e, Long id) {
        e.setId(id);
    }
}
