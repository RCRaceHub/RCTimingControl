package dev.monkeypatch.rctiming.query.entry;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;

/** The entry list one class of a meeting shows an official, competitor names and transponders included. */
@Service
@ReadTransaction
public class AdminEntryQueryService {

    private final DSLContext dsl;

    public AdminEntryQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Every entry of the class at the meeting, withdrawn ones included, oldest submission first. The competitor name
     * is a left join, so it is null if the competitor row is missing.
     */
    public List<AdminEntryDto> listEntriesForClass(Long eventId, Long eventClassId) {
        return dsl.select(
                        ENTRIES.ID,
                        ENTRIES.COMPETITOR_ID,
                        COMPETITORS.DISPLAY_NAME,
                        ENTRIES.TRANSPONDER_NUMBER,
                        ENTRIES.SECONDARY_TRANSPONDER_NUMBER,
                        ENTRIES.IMPORTED_TRANSPONDER_NUMBER,
                        ENTRIES.IMPORTED_SECONDARY_TRANSPONDER_NUMBER,
                        ENTRIES.STATUS,
                        ENTRIES.SUBMITTED_AT,
                        ENTRIES.WITHDRAWN_AT)
                .from(ENTRIES)
                .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .where(ENTRIES.EVENT_ID.eq(eventId))
                .and(ENTRIES.EVENT_CLASS_ID.eq(eventClassId))
                .orderBy(ENTRIES.SUBMITTED_AT.asc())
                .fetch(r -> new AdminEntryDto(
                        r.get(ENTRIES.ID),
                        r.get(ENTRIES.COMPETITOR_ID),
                        r.get(COMPETITORS.DISPLAY_NAME),
                        r.get(ENTRIES.TRANSPONDER_NUMBER),
                        r.get(ENTRIES.SECONDARY_TRANSPONDER_NUMBER),
                        r.get(ENTRIES.IMPORTED_TRANSPONDER_NUMBER),
                        r.get(ENTRIES.IMPORTED_SECONDARY_TRANSPONDER_NUMBER),
                        r.get(ENTRIES.STATUS),
                        r.get(ENTRIES.SUBMITTED_AT),
                        r.get(ENTRIES.WITHDRAWN_AT)));
    }
}
