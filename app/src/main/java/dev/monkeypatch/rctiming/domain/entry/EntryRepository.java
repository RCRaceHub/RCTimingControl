package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntriesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;

/**
 * Repository for {@link Entry} ({@code entries}); every finder returns entries of any status unless it says otherwise.
 */
@Repository
public class EntryRepository extends JooqRepository<Entry, EntriesRecord> {

    private static final EntryMapper MAPPER = Mappers.getMapper(EntryMapper.class);

    public EntryRepository(DSLContext dsl) {
        super(dsl, ENTRIES, ENTRIES.ID);
    }

    /** For the round generator: an event class's entries with the given status. */
    public List<Entry> findByEventClassIdAndStatus(Long eventClassId, EntryStatus status) {
        return findWhere(ENTRIES.EVENT_CLASS_ID.eq(eventClassId).and(ENTRIES.STATUS.eq(status.name())));
    }

    /** An event class's entries that are not withdrawn, for removing the class from its event. */
    public List<Entry> findActiveByEventClassId(Long eventClassId) {
        return findWhere(ENTRIES.EVENT_CLASS_ID.eq(eventClassId).and(ENTRIES.STATUS.ne(EntryStatus.WITHDRAWN.name())));
    }

    /** For the RaceHub import (L7): upsert by the source's entry id. */
    public Optional<Entry> findByExternalSourceAndExternalEntryId(String externalSource, String externalEntryId) {
        return findOne(ENTRIES.EXTERNAL_SOURCE.eq(externalSource).and(ENTRIES.EXTERNAL_ENTRY_ID.eq(externalEntryId)));
    }

    /** Every entry a competitor has, of any status and in any event. */
    public List<Entry> findByCompetitorId(Long competitorId) {
        return findWhere(ENTRIES.COMPETITOR_ID.eq(competitorId));
    }

    /** Every entry in the event, of any status. */
    public List<Entry> findByEventId(Long eventId) {
        return findWhere(ENTRIES.EVENT_ID.eq(eventId));
    }

    /**
     * Loads an entry for the transponder swap's read-then-write (L11). Call it inside the transaction that makes
     * the change; {@link JooqRepository} says why no row lock is needed.
     */
    public Optional<Entry> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    protected Entry toEntity(EntriesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Entry e, EntriesRecord r) {
        MAPPER.toRecord(e, r);
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
