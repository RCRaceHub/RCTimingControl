package dev.monkeypatch.rctiming.query.boards;

import dev.monkeypatch.rctiming.domain.event.PracticeRanking;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.RunOrderSort;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SortField;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * jOOQ read side of the spectator boards (L12).
 * Everything is scoped to one event, which is the one asked for or, by default, the event
 * that is racing now. Called anonymously from the board endpoints, so it returns display data only. Each method is its
 * own read snapshot, so a board that calls several can see a race move between them. "Run order" below is the
 * round's sequence in the event, then the race's sequence in the round, then
 * heat number, then id.
 */
@Component
@ReadTransaction
public class BoardQuery {

    /** The event a board is showing. */
    public record BoardEvent(long id, String name) {}

    private final DSLContext dsl;

    public BoardQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * The event to show: {@code requested} if it exists (empty if it doesn't). With no request,
     * the event with a running or stopped race, else the most recent {@code IN_PROGRESS} event,
     * so a board keeps its event between races. Empty only when neither exists.
     */
    public Optional<BoardEvent> resolveEvent(Long requested) {
        if (requested != null) {
            return dsl.select(EVENTS.ID, EVENTS.NAME).from(EVENTS).where(EVENTS.ID.eq(requested))
                    .fetchOptional(r -> new BoardEvent(r.get(EVENTS.ID), r.get(EVENTS.NAME)));
        }
        Optional<BoardEvent> racing = dsl.select(EVENTS.ID, EVENTS.NAME)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENTS).on(EVENTS.ID.eq(ROUNDS.EVENT_ID))
                .where(RACES.STATUS.in("RUNNING", "STOPPED"))
                .orderBy(RACES.STARTED_AT.desc().nullsLast(), RACES.ID.desc())
                .limit(1)
                .fetchOptional(r -> new BoardEvent(r.get(EVENTS.ID), r.get(EVENTS.NAME)));
        if (racing.isPresent()) {
            return racing;
        }
        return dsl.select(EVENTS.ID, EVENTS.NAME)
                .from(EVENTS)
                .where(EVENTS.STATUS.eq("IN_PROGRESS"))
                .orderBy(EVENTS.EVENT_DATE.desc(), EVENTS.ID.desc())
                .limit(1)
                .fetchOptional(r -> new BoardEvent(r.get(EVENTS.ID), r.get(EVENTS.NAME)));
    }

    /** How the event ranks practice, read on the read pool because the board polls it anonymously. */
    public PracticeRanking practiceRanking(long eventId) {
        return dsl.select(EVENTS.PRACTICE_RANKING).from(EVENTS).where(EVENTS.ID.eq(eventId))
                .fetchOptional(r -> PracticeRanking.valueOf(r.get(EVENTS.PRACTICE_RANKING)))
                .orElse(PracticeRanking.FASTEST);
    }

    /** The race on track: a running race, or failing that a stopped one. */
    public Optional<BoardRaceDto> currentRace(long eventId) {
        return first(eventId, RACES.STATUS.eq("RUNNING"), runOrder())
                .or(() -> first(eventId, RACES.STATUS.eq("STOPPED"), runOrder()));
    }

    /**
     * The race up next: one already called to the grid, else the first pending race in run
     * order. Grid first, because a race director who skips ahead (CTRL-09) calls the grid on
     * the race they skipped to while earlier races stay pending.
     */
    public Optional<BoardRaceDto> nextRace(long eventId) {
        return first(eventId, RACES.STATUS.eq("GRID"), runOrder())
                .or(() -> first(eventId, RACES.STATUS.eq("PENDING"), runOrder()));
    }

    /** The race that finished most recently. */
    public Optional<BoardRaceDto> lastCompletedRace(long eventId) {
        return first(eventId, RACES.STATUS.eq("FINISHED"),
                List.of(RACES.FINISHED_AT.desc().nullsLast(), RACES.ID.desc()));
    }

    /**
     * The qualifying heat that finished most recently, whatever has been raced since, so the qualifying table can
     * stay up once the finals have started. It is the latest across all classes, not the class now on track.
     */
    public Optional<BoardRaceDto> lastCompletedQualifier(long eventId) {
        return lastCompletedOfType(eventId, "QUALIFIER");
    }

    /** The practice heat that finished most recently, for the practice table before qualifying has started. */
    public Optional<BoardRaceDto> lastCompletedPractice(long eventId) {
        return lastCompletedOfType(eventId, "PRACTICE");
    }

    private Optional<BoardRaceDto> lastCompletedOfType(long eventId, String roundType) {
        return first(eventId, RACES.STATUS.eq("FINISHED").and(ROUNDS.TYPE.eq(roundType)),
                List.of(RACES.FINISHED_AT.desc().nullsLast(), RACES.ID.desc()));
    }

    /** The other finished heats in this race's round and class, whose laps count towards the round's fastest. */
    public List<Long> finishedRaceIdsInRound(long raceId) {
        var race = RACES.as("this_race");
        return dsl.select(RACES.ID)
                .from(RACES)
                .join(race).on(race.ROUND_ID.eq(RACES.ROUND_ID).and(race.EVENT_CLASS_ID.eq(RACES.EVENT_CLASS_ID)))
                .where(race.ID.eq(raceId))
                .and(RACES.ID.ne(raceId))
                .and(RACES.STATUS.eq("FINISHED"))
                .orderBy(RACES.ID)
                .fetch(RACES.ID);
    }

    /** The 1-based grid place of each entry in a race; an entry with no grid place is left out. */
    public Map<Long, Integer> gridPositions(long raceId) {
        Map<Long, Integer> grid = new HashMap<>();
        dsl.select(RACE_ENTRIES.ENTRY_ID, RACE_ENTRIES.GRID_POSITION)
                .from(RACE_ENTRIES)
                .where(RACE_ENTRIES.RACE_ID.eq(raceId), RACE_ENTRIES.GRID_POSITION.isNotNull())
                .forEach(r -> grid.put(r.get(RACE_ENTRIES.ENTRY_ID), r.get(RACE_ENTRIES.GRID_POSITION)));
        return grid;
    }

    /** The competitor behind each entry, so a board can apply that competitor's public name setting. */
    public Map<Long, Long> competitorIds(Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> ids = new HashMap<>();
        dsl.select(ENTRIES.ID, ENTRIES.COMPETITOR_ID).from(ENTRIES).where(ENTRIES.ID.in(entryIds))
                .forEach(r -> ids.put(r.get(ENTRIES.ID), r.get(ENTRIES.COMPETITOR_ID)));
        return ids;
    }

    /** The car number each entry raced under, for names that stand in a number when the competitor is hidden. */
    public Map<Long, String> carNumbers(Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> numbers = new HashMap<>();
        dsl.select(RACE_ENTRIES.ENTRY_ID, RACE_ENTRIES.CAR_NUMBER).from(RACE_ENTRIES)
                .where(RACE_ENTRIES.ENTRY_ID.in(entryIds)).and(RACE_ENTRIES.CAR_NUMBER.isNotNull())
                .orderBy(RACE_ENTRIES.ID)
                .forEach(r -> numbers.put(r.get(RACE_ENTRIES.ENTRY_ID), String.valueOf(r.get(RACE_ENTRIES.CAR_NUMBER))));
        return numbers;
    }

    private static List<SortField<?>> runOrder() {
        return RunOrderSort.ascending();
    }

    private Optional<BoardRaceDto> first(long eventId, Condition status, List<SortField<?>> order) {
        return dsl
                .select(
                        RACES.ID,
                        ROUNDS.TYPE,
                        ROUNDS.ROUND_NUMBER,
                        RACING_CLASSES.NAME.as("className"),
                        RACES.HEAT_NUMBER,
                        RACES.FINAL_LETTER,
                        RACES.STATUS,
                        RACING_CLASSES.COLOUR)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .and(status)
                .orderBy(order)
                .limit(1)
                .fetchOptional(BoardQuery::toDto);
    }

    private static BoardRaceDto toDto(Record r) {
        String roundType = r.get(ROUNDS.TYPE);
        int roundNumber = r.get(ROUNDS.ROUND_NUMBER);
        String className = r.get("className", String.class);
        int heatNumber = r.get(RACES.HEAT_NUMBER);
        String finalLetter = r.get(RACES.FINAL_LETTER);
        return new BoardRaceDto(
                r.get(RACES.ID),
                RaceLabel.of(roundType, roundNumber, className, heatNumber, finalLetter),
                roundType,
                roundNumber,
                className,
                heatNumber,
                finalLetter,
                r.get(RACES.STATUS),
                r.get(RACING_CLASSES.COLOUR));
    }
}
