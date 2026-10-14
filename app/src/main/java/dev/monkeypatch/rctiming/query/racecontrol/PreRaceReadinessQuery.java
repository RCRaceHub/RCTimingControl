package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.SpeechName;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.List;

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
 * - gridCall: drivers due on track in grid order for the target race, with whether each has
 *   checked in (L11) and RaceHub's arrival mark
 * - marshalDuty: drivers from the immediately preceding race with per-event absence counts
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
        List<MarshalDutyRowDto> marshalDuty;

        if (previousRaceRow != null) {
            // Build marshalDuty from previous race's entries + absence counts
            long previousRaceId = previousRaceRow.get(RACES.ID);

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
                    .where(RACE_ENTRIES.RACE_ID.eq(previousRaceId))
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
                        ENTRIES.CHECKED_IN_AT,
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
                        r.get(ENTRIES.CHECKED_IN_AT) != null,
                        r.get(ENTRIES.RACEHUB_ARRIVAL)
                ));

        return new PreRaceReadinessDto(raceId, raceLabel, firstRaceOfEvent, gridCall, marshalDuty);
    }
}
