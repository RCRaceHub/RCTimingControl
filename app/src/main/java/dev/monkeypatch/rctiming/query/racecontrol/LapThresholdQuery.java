package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.jooq.generated.tables.TrackLapThresholds;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;

/**
 * The lap limits the meeting's track sets for a race (TRACK-02, TRACK-03): for each class, its own limits at that
 * track, else the track's default for every class. A track, class or limit that is not set means no limit.
 * A class with limits of its own does not borrow the default's other limit.
 */
@Component
@ReadTransaction
public class LapThresholdQuery {

    private final DSLContext dsl;

    public LapThresholdQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * The shortest lap that counts, in milliseconds, by entry id, for the entries of one race that have one. An
     * entry's class limit is used when it has a minimum set, else the track default's minimum; an entry with neither
     * is left out of the map. Judged by the entry's own class, not the race's.
     */
    public Map<Long, Long> minimumLapsForRace(long raceId) {
        TrackLapThresholds classRule = TrackLapThresholds.TRACK_LAP_THRESHOLDS.as("class_rule");
        TrackLapThresholds trackRule = TrackLapThresholds.TRACK_LAP_THRESHOLDS.as("track_rule");
        Map<Long, Long> minimums = new HashMap<>();
        dsl.select(RACE_ENTRIES.ENTRY_ID, classRule.MIN_LAP_MS, trackRule.MIN_LAP_MS)
                .from(RACE_ENTRIES)
                .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                .join(EVENTS).on(EVENTS.ID.eq(ENTRIES.EVENT_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(ENTRIES.EVENT_CLASS_ID))
                .leftJoin(classRule).on(classRule.TRACK_ID.eq(EVENTS.TRACK_ID)
                        .and(classRule.RACING_CLASS_ID.eq(EVENT_CLASSES.RACING_CLASS_ID)))
                .leftJoin(trackRule).on(trackRule.TRACK_ID.eq(EVENTS.TRACK_ID)
                        .and(trackRule.RACING_CLASS_ID.isNull()))
                .where(RACE_ENTRIES.RACE_ID.eq(raceId))
                .forEach(row -> {
                    Integer minLapMs = row.value2() != null ? row.value2() : row.value3();
                    if (minLapMs != null) {
                        minimums.put(row.value1(), minLapMs.longValue());
                    }
                });
        return minimums;
    }

    /**
     * How long after the clock runs out the race waits for another crossing, in milliseconds, if its track says.
     * If the race's class has limits of its own they decide, and a class with no wait set has none; otherwise the
     * track default applies. Empty when no limit applies or the race has no track.
     */
    public Optional<Long> maxLastLapMsForRace(long raceId) {
        TrackLapThresholds classRule = TrackLapThresholds.TRACK_LAP_THRESHOLDS.as("class_rule");
        TrackLapThresholds trackRule = TrackLapThresholds.TRACK_LAP_THRESHOLDS.as("track_rule");
        return dsl.select(classRule.ID, classRule.MAX_LAST_LAP_MS, trackRule.MAX_LAST_LAP_MS)
                .from(RACES)
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(EVENTS).on(EVENTS.ID.eq(EVENT_CLASSES.EVENT_ID))
                .leftJoin(classRule).on(classRule.TRACK_ID.eq(EVENTS.TRACK_ID)
                        .and(classRule.RACING_CLASS_ID.eq(EVENT_CLASSES.RACING_CLASS_ID)))
                .leftJoin(trackRule).on(trackRule.TRACK_ID.eq(EVENTS.TRACK_ID)
                        .and(trackRule.RACING_CLASS_ID.isNull()))
                .where(RACES.ID.eq(raceId))
                // A class deleted from under its limits leaves them as a second track default, so take any one row
                .fetch().stream().findFirst()
                // The class's own limits are used as a whole: with no wait set, that class has none
                .map(row -> row.value1() != null ? row.value2() : row.value3())
                .map(Integer::longValue);
    }
}
