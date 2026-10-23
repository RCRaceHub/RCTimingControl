package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.SpeechName;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalAbsences.MARSHAL_ABSENCES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * jOOQ read-side query for the pre-race readiness view (CTRL-02, CTRL-07).
 * Produces a PreRaceReadinessDto for a given raceId, containing:
 * - gridCall: drivers due on track in grid order for the target race, with RaceHub's arrival mark
 * - marshalDuty: drivers from the immediately preceding race with per-event absence counts, left out while a
 *   bump-up final waits on the final below and without those who moved up once it has finished
 *
 * All reads go through DSLContext.
 */
@Component
@ReadTransaction
public class PreRaceReadinessQuery {

    private final DSLContext dsl;

    public PreRaceReadinessQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Load the pre-race readiness data for the given race.
     *
     * <p>The grid call has a slot per entry on the race, in grid order with unseeded entries (position 0) last. The
     * marshal duty (empty with {@code marshalsPending} while a bump-up final waits on the final below, and without
     * the drivers who moved up once it has finished) is the drivers of the race before this one in the event's run order, or for the first race of the
     * event the last race of its own round. The returned {@code startType} is null and the stagger interval 0: the
     * caller adds the race's start details with {@link PreRaceReadinessDto#withStart}.
     *
     * @param raceId the race to load readiness for
     * @return a fully populated PreRaceReadinessDto
     * @throws EntityNotFoundException if no race with this ID exists
     */
    public PreRaceReadinessDto load(long raceId) {
        // Fetch target race row with round and class info
        var targetRow = dsl
                .select(
                        RACES.ID,
                        RACES.HEAT_NUMBER,
                        RACES.FINAL_LETTER,
                        RACES.STATUS,
                        RACES.SEQUENCE_IN_ROUND,
                        RACES.EVENT_CLASS_ID,
                        RACES.BUMP_SLOTS,
                        ROUNDS.EVENT_ID,
                        ROUNDS.SEQUENCE_IN_EVENT,
                        ROUNDS.ROUND_NUMBER,
                        ROUNDS.TYPE,
                        RACING_CLASSES.NAME.as("className"))
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(RACES.ID.eq(raceId))
                .fetchOne();

        if (targetRow == null) {
            throw new EntityNotFoundException("Race not found: " + raceId);
        }

        int heatNumber = targetRow.get(RACES.HEAT_NUMBER);
        String finalLetter = targetRow.get(RACES.FINAL_LETTER);
        int sequenceInRound = targetRow.get(RACES.SEQUENCE_IN_ROUND);
        long eventId = targetRow.get(ROUNDS.EVENT_ID);
        int sequenceInEvent = targetRow.get(ROUNDS.SEQUENCE_IN_EVENT);
        int roundNumber = targetRow.get(ROUNDS.ROUND_NUMBER);
        String roundType = targetRow.get(ROUNDS.TYPE);
        String className = targetRow.get("className", String.class);

        // Build raceLabel from round type
        String raceLabel = RaceLabel.of(roundType, roundNumber, className, heatNumber, finalLetter);

        // Resolve the previous race in the event run order
        // "Previous" = highest (sequence_in_event, sequence_in_round) tuple strictly less than target
        var previousRaceRow = dsl
                .select(RACES.ID, ROUNDS.EVENT_ID)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .and(
                        ROUNDS.SEQUENCE_IN_EVENT.lt(sequenceInEvent)
                        .or(ROUNDS.SEQUENCE_IN_EVENT.eq(sequenceInEvent)
                                .and(RACES.SEQUENCE_IN_ROUND.lt(sequenceInRound)))
                )
                .orderBy(ROUNDS.SEQUENCE_IN_EVENT.desc(), RACES.SEQUENCE_IN_ROUND.desc())
                .limit(1)
                .fetchOne();

        boolean firstRaceOfEvent = (previousRaceRow == null);

        // The first race of the event has no race before it, so the last race of its own round
        // marshals it, the way the last heat of a round marshals the first heat of the next.
        Long marshalRaceId = previousRaceRow != null ? previousRaceRow.get(RACES.ID) : null;
        if (marshalRaceId == null) {
            var lastInRound = dsl
                    .select(RACES.ID)
                    .from(RACES)
                    .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                    .where(ROUNDS.EVENT_ID.eq(eventId))
                    .and(ROUNDS.SEQUENCE_IN_EVENT.eq(sequenceInEvent))
                    .orderBy(RACES.SEQUENCE_IN_ROUND.desc())
                    .limit(1)
                    .fetchOne();
            if (lastInRound != null && lastInRound.get(RACES.ID) != raceId) {
                marshalRaceId = lastInRound.get(RACES.ID);
            }
        }

        // A final that takes bump-ups from the final below can't say who marshals it until that final finishes: the
        // drivers who move up race instead. So the list is held back until then, and then leaves them out.
        boolean bumpUpFinal = marshalRaceId != null && targetRow.get(RACES.BUMP_SLOTS) > 0
                && isLowerFinalOfSameClass(marshalRaceId, finalLetter, targetRow.get(RACES.EVENT_CLASS_ID));
        boolean marshalsPending = bumpUpFinal && !isFinished(marshalRaceId);
        if (marshalsPending) {
            marshalRaceId = null;
        }
        Set<Long> bumpedIn = marshalRaceId == null || !bumpUpFinal ? Set.of()
                : new HashSet<>(dsl.select(RACE_ENTRIES.ENTRY_ID).from(RACE_ENTRIES)
                        .where(RACE_ENTRIES.RACE_ID.eq(raceId)).and(RACE_ENTRIES.BUMPED.isTrue())
                        .fetch(RACE_ENTRIES.ENTRY_ID));

        List<MarshalDutyRowDto> marshalDuty;
        if (marshalRaceId != null) {
            // Build marshalDuty from the marshalling race's entries + absence counts

            // Correlated subquery alias for absence count
            var absenceCount = DSL.select(DSL.count())
                    .from(MARSHAL_ABSENCES)
                    .where(MARSHAL_ABSENCES.ENTRY_ID.eq(RACE_ENTRIES.ENTRY_ID))
                    .and(MARSHAL_ABSENCES.EVENT_ID.eq(eventId))
                    .asField("missedThisEvent");

            marshalDuty = dsl
                    .select(
                            RACE_ENTRIES.ENTRY_ID,
                            DSL.coalesce(COMPETITORS.DISPLAY_NAME, DSL.val("Unknown")).as("driverName"),
                            absenceCount)
                    .from(RACE_ENTRIES)
                    .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                    .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                    .where(RACE_ENTRIES.RACE_ID.eq(marshalRaceId))
                    .and(bumpedIn.isEmpty() ? DSL.noCondition() : RACE_ENTRIES.ENTRY_ID.notIn(bumpedIn))
                    .orderBy(RACE_ENTRIES.GRID_POSITION.asc().nullsLast(), ENTRIES.ID.asc())
                    .fetch(r -> new MarshalDutyRowDto(
                            r.get(RACE_ENTRIES.ENTRY_ID),
                            r.get("driverName", String.class),
                            null, // not read yet, though race_entries.car_number holds it (ENTRY-03)
                            r.get("missedThisEvent", Long.class)
                    ));
        } else {
            marshalDuty = List.of();
        }

        // Build gridCall for the target race
        List<GridCallSlotDto> gridCall = dsl
                .select(
                        RACE_ENTRIES.ID,
                        RACE_ENTRIES.ENTRY_ID,
                        RACE_ENTRIES.GRID_POSITION,
                        DSL.coalesce(COMPETITORS.DISPLAY_NAME, DSL.val("Unknown")).as("driverName"),
                        COMPETITORS.SPOKEN_NAME,
                        RACING_CLASSES.NAME.as("className"),
                        ENTRIES.RACEHUB_ARRIVAL)
                .from(RACE_ENTRIES)
                .join(RACES).on(RACES.ID.eq(RACE_ENTRIES.RACE_ID))
                .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(RACE_ENTRIES.RACE_ID.eq(raceId))
                .orderBy(RACE_ENTRIES.GRID_POSITION.asc().nullsLast(), ENTRIES.ID.asc())
                .fetch(r -> new GridCallSlotDto(
                        r.get(RACE_ENTRIES.GRID_POSITION) != null
                                ? r.get(RACE_ENTRIES.GRID_POSITION)
                                : 0,
                        r.get(RACE_ENTRIES.ENTRY_ID),
                        r.get("driverName", String.class),
                        SpeechName.of(r.get(COMPETITORS.SPOKEN_NAME), r.get("driverName", String.class)),
                        null, // not read yet, though race_entries.car_number holds it (ENTRY-03)
                        r.get("className", String.class),
                        r.get(ENTRIES.RACEHUB_ARRIVAL)
                ));

        return new PreRaceReadinessDto(raceId, eventId, raceLabel, firstRaceOfEvent, gridCall, marshalDuty,
                marshalsPending, null, 0);
    }

    /**
     * Whether {@code marshalRaceId} is a final of the same class lower than the one lettered {@code targetLetter}
     * (B under A, and the zombie round's Z under A), so the drivers who move up from it are the target's bump-ups.
     * Runs in the caller's read snapshot.
     */
    private boolean isLowerFinalOfSameClass(long marshalRaceId, String targetLetter, long eventClassId) {
        return targetLetter != null && dsl.fetchExists(dsl.selectOne().from(RACES)
                .where(RACES.ID.eq(marshalRaceId))
                .and(RACES.EVENT_CLASS_ID.eq(eventClassId))
                .and(RACES.FINAL_LETTER.gt(targetLetter)));
    }

    private boolean isFinished(long raceId) {
        return dsl.fetchExists(dsl.selectOne().from(RACES)
                .where(RACES.ID.eq(raceId))
                .and(RACES.STATUS.eq(RaceStatus.FINISHED.name())));
    }
}
