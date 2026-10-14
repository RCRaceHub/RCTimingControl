package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * jOOQ read-side query for the run-order list.
 * Joins Round → Race → EventClass → RacingClass to produce an ordered run list
 * for the cockpit left panel.
 */
@Component
@ReadTransaction
public class RunOrderQuery {

    private final DSLContext dsl;

    public RunOrderQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Returns all races for the given event in run order: round by round, and within a round in the order
     * the generator numbered them ({@code sequence_in_round}). The heat number is not enough, since each class
     * in a round starts again at heat 1. The boards and the pre-race check use the same order.
     *
     * @param eventId the event to load the run order for
     * @return ordered list of RunOrderItemDto
     */
    public List<RunOrderItemDto> findForEvent(long eventId) {
        return dsl
                .select(
                        RACES.ID,
                        ROUNDS.SEQUENCE_IN_EVENT,
                        ROUNDS.ROUND_NUMBER,
                        ROUNDS.TYPE,
                        RACING_CLASSES.NAME.as("className"),
                        RACES.HEAT_NUMBER,
                        RACES.FINAL_LETTER,
                        RACES.STATUS,
                        RACES.SEQUENCE_IN_ROUND,
                        RACES.STARTED_AT)
                .from(ROUNDS)
                .join(RACES).on(RACES.ROUND_ID.eq(ROUNDS.ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(ROUNDS.EVENT_ID.eq(eventId))
                .orderBy(ROUNDS.SEQUENCE_IN_EVENT.asc(), RACES.SEQUENCE_IN_ROUND.asc(),
                        RACES.HEAT_NUMBER.asc(), RACES.ID.asc())
                .fetch(r -> new RunOrderItemDto(
                        r.get(RACES.ID),
                        r.get(ROUNDS.SEQUENCE_IN_EVENT),
                        r.get(ROUNDS.ROUND_NUMBER),
                        r.get(ROUNDS.TYPE),
                        r.get("className", String.class),
                        r.get(RACES.HEAT_NUMBER),
                        r.get(RACES.FINAL_LETTER),
                        r.get(RACES.STATUS),
                        r.get(RACES.SEQUENCE_IN_ROUND),
                        r.get(RACES.STARTED_AT)
                ));
    }
}
