package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.format.PointsFinalsConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatConfigConverter;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ScoringSystems.SCORING_SYSTEMS;

/**
 * Reads an event's finished races for the officials' printouts. Called from
 * {@code RaceControlController} on a request thread; one snapshot on the read pool.
 */
@Component
@ReadTransaction
public class EventResultsQuery {

    private static final RaceFormatConfigConverter CONFIG = new RaceFormatConfigConverter();

    private final DSLContext dsl;
    private final ResultSnapshotQuery snapshotQuery;

    public EventResultsQuery(DSLContext dsl, ResultSnapshotQuery snapshotQuery) {
        this.dsl = dsl;
        this.snapshotQuery = snapshotQuery;
    }

    /**
     * The event's finished races in run order, each with its stored result and starting grid. Practice rounds are
     * included; the caller picks what it prints. The lap history is left out (the rows and times are enough for
     * the sheets; {@link #forRace} keeps it). Times in the results are milliseconds.
     *
     * @throws EntityNotFoundException if there is no such event
     */
    public EventResultsDto forEvent(long eventId) {
        return load(eventId, DSL.trueCondition(), false);
    }

    /**
     * One finished race of its event, shaped like {@link #forEvent} so the heat result page can use the same
     * rows: the event's name and date and a single race.
     *
     * @throws EntityNotFoundException if there is no such race or it has no stored result yet
     */
    public EventResultsDto forRace(long raceId) {
        Long eventId = dsl.select(ROUNDS.EVENT_ID)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(RACES.ID.eq(raceId))
                .fetchOne(ROUNDS.EVENT_ID);
        if (eventId == null) {
            throw new EntityNotFoundException("No race " + raceId);
        }
        EventResultsDto results = load(eventId, RACES.ID.eq(raceId), true);
        if (results.races().isEmpty()) {
            throw new EntityNotFoundException("No result snapshot for race " + raceId);
        }
        return results;
    }

    /** {@code withLaps} keeps each race's lap history, which only the single-heat lap sheet uses and which is the bulk of the payload. */
    private EventResultsDto load(long eventId, Condition raceFilter, boolean withLaps) {
        var event = dsl.select(EVENTS.NAME, EVENTS.EVENT_DATE)
                .from(EVENTS)
                .where(EVENTS.ID.eq(eventId))
                .fetchOne();
        if (event == null) {
            throw new EntityNotFoundException("No event " + eventId);
        }

        var finished = dsl
                .select(RACES.ID, RACES.EVENT_CLASS_ID, RACING_CLASSES.NAME, ROUNDS.ROUND_NUMBER, ROUNDS.TYPE,
                        RACES.HEAT_NUMBER, RACES.FINAL_LETTER)
                .from(ROUNDS)
                .join(RACES).on(RACES.ROUND_ID.eq(ROUNDS.ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .join(RESULT_SNAPSHOTS).on(RESULT_SNAPSHOTS.RACE_ID.eq(RACES.ID))
                .where(ROUNDS.EVENT_ID.eq(eventId), raceFilter)
                .orderBy(ROUNDS.SEQUENCE_IN_EVENT.asc(), RACES.SEQUENCE_IN_ROUND.asc(),
                        RACES.HEAT_NUMBER.asc(), RACES.ID.asc())
                .fetch();

        List<Long> raceIds = finished.getValues(RACES.ID);
        Map<Long, Map<Long, Integer>> grids = startPositions(raceIds);
        Map<Long, String> scoringSystems = scoringSystemNames(finished.getValues(RACES.EVENT_CLASS_ID));

        // Each class's round points and the event's final points are worked out once, not once per race
        ResultPointsCache pointsCache = new ResultPointsCache();
        List<EventResultsDto.Race> races = finished.map(r -> {
            long raceId = r.get(RACES.ID);
            return new EventResultsDto.Race(
                    raceId,
                    r.get(RACES.EVENT_CLASS_ID),
                    r.get(RACING_CLASSES.NAME),
                    r.get(ROUNDS.ROUND_NUMBER),
                    r.get(ROUNDS.TYPE),
                    r.get(RACES.HEAT_NUMBER),
                    r.get(RACES.FINAL_LETTER),
                    withLaps ? snapshotQuery.load(raceId, pointsCache)
                            : withoutLaps(snapshotQuery.load(raceId, pointsCache)),
                    grids.getOrDefault(raceId, Map.of()),
                    scoringSystems.get(r.get(RACES.EVENT_CLASS_ID)));
        });
        return new EventResultsDto(eventId, event.get(EVENTS.NAME), event.get(EVENTS.EVENT_DATE), races);
    }

    /**
     * The name of the race scoring system each event class was set from, by event class id; a class whose format was
     * set by hand, is not a Points Finals one, or names a system that has been deleted is missing. The class's own
     * snapshot says which system it took its values from, so a later edit to the system's values does not change
     * this, though renaming the system does. A snapshot that cannot be read counts as set by hand.
     */
    private Map<Long, String> scoringSystemNames(List<Long> eventClassIds) {
        Map<Long, Long> systemOfClass = new HashMap<>();
        dsl.select(EVENT_CLASSES.ID, EVENT_CLASSES.CONFIG_SNAPSHOT)
                .from(EVENT_CLASSES)
                .where(EVENT_CLASSES.ID.in(eventClassIds.stream().distinct().toList()))
                .forEach(r -> {
                    try {
                        if (CONFIG.convertToEntityAttribute(r.get(EVENT_CLASSES.CONFIG_SNAPSHOT)) instanceof PointsFinalsConfig p
                                && p.scoringSystemId() != null) {
                            systemOfClass.put(r.get(EVENT_CLASSES.ID), p.scoringSystemId());
                        }
                    } catch (IllegalArgumentException unreadable) {
                        // The label is a courtesy: a snapshot this version cannot read must not block the printout.
                    }
                });
        if (systemOfClass.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = dsl.select(SCORING_SYSTEMS.ID, SCORING_SYSTEMS.NAME)
                .from(SCORING_SYSTEMS)
                .where(SCORING_SYSTEMS.ID.in(systemOfClass.values()))
                .fetchMap(SCORING_SYSTEMS.ID, SCORING_SYSTEMS.NAME);
        Map<Long, String> byClass = new HashMap<>();
        systemOfClass.forEach((classId, systemId) -> {
            String name = names.get(systemId);
            if (name != null) {
                byClass.put(classId, name);
            }
        });
        return byClass;
    }

    private static ResultSnapshotDto withoutLaps(ResultSnapshotDto snapshot) {
        return new ResultSnapshotDto(snapshot.raceId(), snapshot.raceLabel(), snapshot.finishedAt(),
                snapshot.positions(), List.of(), snapshot.clubBranding());
    }

    /** Grid position by entry id for each of the races; an entry with no grid position is left out. */
    private Map<Long, Map<Long, Integer>> startPositions(List<Long> raceIds) {
        Map<Long, Map<Long, Integer>> byRace = new HashMap<>();
        if (raceIds.isEmpty()) {
            return byRace;
        }
        dsl.select(RACE_ENTRIES.RACE_ID, RACE_ENTRIES.ENTRY_ID, RACE_ENTRIES.GRID_POSITION)
                .from(RACE_ENTRIES)
                .where(RACE_ENTRIES.RACE_ID.in(raceIds), RACE_ENTRIES.GRID_POSITION.isNotNull())
                .forEach(r -> byRace
                        .computeIfAbsent(r.get(RACE_ENTRIES.RACE_ID), k -> new HashMap<>())
                        .put(r.get(RACE_ENTRIES.ENTRY_ID), r.get(RACE_ENTRIES.GRID_POSITION)));
        return byRace;
    }
}
