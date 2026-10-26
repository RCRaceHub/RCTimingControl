package dev.monkeypatch.rctiming.query;

import org.jooq.SortField;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * The one ordering of an event's races, so the run order, the boards, the schedule and the pre-race check cannot
 * disagree. A query using it must join both {@code races} and {@code rounds}.
 *
 * <p>A race's {@code run_rank} (set for every race of the event when an official reorders the finals) comes first.
 * Without ranks the order is the generated one: round by round, and within a round by the generator's sequence, then
 * heat number, since each class in a round starts again at heat 1.
 */
public final class RunOrderSort {

    private RunOrderSort() {
    }

    /** The races of an event, first to run first. */
    public static List<SortField<?>> ascending() {
        return List.of(RACES.RUN_RANK.asc().nullsLast(), ROUNDS.SEQUENCE_IN_EVENT.asc(),
                RACES.SEQUENCE_IN_ROUND.asc(), RACES.HEAT_NUMBER.asc(), RACES.ID.asc());
    }
}
