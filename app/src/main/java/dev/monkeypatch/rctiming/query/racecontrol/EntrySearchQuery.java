package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.competitor.SpeechName;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;

/**
 * jOOQ read side of the transponder swap tool (L11): look entries up by the competitor's name. Withdrawn entries are
 * never returned.
 */
@Component
@ReadTransaction
public class EntrySearchQuery {

    /** The most matches one search returns. */
    static final int SEARCH_LIMIT = 25;

    private final DSLContext dsl;

    public EntrySearchQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Case-insensitive substring match on the competitor's name among one event's entries, by name then class,
     * capped at {@link #SEARCH_LIMIT}. A blank query finds nothing. {@code %}, {@code _} and backslash in the query
     * are matched literally.
     */
    public List<EntrySearchDto> searchByName(long eventId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String pattern = "%" + escapeLike(query.trim()) + "%";
        return select(COMPETITORS.DISPLAY_NAME.likeIgnoreCase(pattern, '\\'), eventId);
    }

    private List<EntrySearchDto> select(Condition match, long eventId) {
        return dsl
                .select(
                        ENTRIES.ID,
                        DSL.coalesce(COMPETITORS.DISPLAY_NAME, DSL.val("Unknown")).as("competitorName"),
                        ENTRIES.COMPETITOR_ID,
                        COMPETITORS.SPOKEN_NAME,
                        RACING_CLASSES.NAME.as("className"),
                        ENTRIES.TRANSPONDER_NUMBER,
                        ENTRIES.SECONDARY_TRANSPONDER_NUMBER,
                        ENTRIES.IMPORTED_TRANSPONDER_NUMBER,
                        ENTRIES.IMPORTED_SECONDARY_TRANSPONDER_NUMBER,
                        ENTRIES.THIRD_TRANSPONDER_NUMBER,
                        ENTRIES.FOURTH_TRANSPONDER_NUMBER)
                .from(ENTRIES)
                .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .leftJoin(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(ENTRIES.EVENT_CLASS_ID))
                .leftJoin(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(ENTRIES.EVENT_ID.eq(eventId))
                .and(ENTRIES.STATUS.ne("WITHDRAWN"))
                .and(match)
                .orderBy(COMPETITORS.DISPLAY_NAME.asc().nullsLast(), RACING_CLASSES.NAME.asc(), ENTRIES.ID.asc())
                .limit(SEARCH_LIMIT)
                .fetch(EntrySearchQuery::toDto);
    }

    private static EntrySearchDto toDto(Record r) {
        return new EntrySearchDto(
                r.get(ENTRIES.ID),
                r.get("competitorName", String.class),
                r.get(ENTRIES.COMPETITOR_ID),
                r.get(COMPETITORS.SPOKEN_NAME),
                SpeechName.of(r.get(COMPETITORS.SPOKEN_NAME), r.get("competitorName", String.class)),
                r.get("className", String.class),
                r.get(ENTRIES.TRANSPONDER_NUMBER),
                r.get(ENTRIES.SECONDARY_TRANSPONDER_NUMBER),
                r.get(ENTRIES.IMPORTED_TRANSPONDER_NUMBER),
                r.get(ENTRIES.IMPORTED_SECONDARY_TRANSPONDER_NUMBER),
                r.get(ENTRIES.THIRD_TRANSPONDER_NUMBER),
                r.get(ENTRIES.FOURTH_TRANSPONDER_NUMBER));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
