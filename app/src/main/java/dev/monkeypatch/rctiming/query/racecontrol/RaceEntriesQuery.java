package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;

@Component
@ReadTransaction
public class RaceEntriesQuery {

    private final DSLContext dsl;

    public RaceEntriesQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<RaceEntryDto> findForRace(long raceId) {
        return dsl
                .select(
                        RACE_ENTRIES.ENTRY_ID,
                        DSL.coalesce(COMPETITORS.DISPLAY_NAME, DSL.val("Unknown")).as("driverName"))
                .from(RACE_ENTRIES)
                .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .where(RACE_ENTRIES.RACE_ID.eq(raceId))
                .orderBy(RACE_ENTRIES.GRID_POSITION.asc().nullsLast())
                .fetch(r -> new RaceEntryDto(
                        r.get(RACE_ENTRIES.ENTRY_ID),
                        r.get("driverName", String.class),
                        null // not read yet, though race_entries.car_number holds it (ENTRY-03)
                ));
    }
}
