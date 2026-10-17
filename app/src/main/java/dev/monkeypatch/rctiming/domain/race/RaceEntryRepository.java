package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RaceEntriesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;

/** Repository for {@link RaceEntry} ({@code race_entries}). */
@Repository
public class RaceEntryRepository extends JooqRepository<RaceEntry, RaceEntriesRecord> {

    public RaceEntryRepository(DSLContext dsl) {
        super(dsl, RACE_ENTRIES, RACE_ENTRIES.ID);
    }

    public List<RaceEntry> findByRaceIdOrderByGridPosition(Long raceId) {
        return findWhere(RACE_ENTRIES.RACE_ID.eq(raceId), RACE_ENTRIES.GRID_POSITION.asc().nullsFirst(), RACE_ENTRIES.ID.asc());
    }

    public Optional<RaceEntry> findByRaceIdAndEntryId(Long raceId, Long entryId) {
        return findWhere(RACE_ENTRIES.RACE_ID.eq(raceId).and(RACE_ENTRIES.ENTRY_ID.eq(entryId))).stream().findFirst();
    }

    @Override
    protected RaceEntry toEntity(RaceEntriesRecord r) {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setId(r.getId());
        raceEntry.setRaceId(r.getRaceId());
        raceEntry.setEntryId(r.getEntryId());
        raceEntry.setGridPosition(r.getGridPosition());
        raceEntry.setBumped(r.getBumped());
        raceEntry.setCarNumber(r.getCarNumber());
        return raceEntry;
    }

    @Override
    protected void toRecord(RaceEntry raceEntry, RaceEntriesRecord r) {
        r.setRaceId(raceEntry.getRaceId());
        r.setEntryId(raceEntry.getEntryId());
        r.setGridPosition(raceEntry.getGridPosition());
        r.setBumped(raceEntry.isBumped());
        r.setCarNumber(raceEntry.getCarNumber());
    }

    @Override
    protected Long idOf(RaceEntry raceEntry) {
        return raceEntry.getId();
    }

    @Override
    protected void setId(RaceEntry raceEntry, Long id) {
        raceEntry.setId(id);
    }
}
