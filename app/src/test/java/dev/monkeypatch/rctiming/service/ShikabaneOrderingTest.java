package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Placing a Shikabane heat by the order of elimination. */
class ShikabaneOrderingTest {

    private static final List<ResultRow> TIMED = List.of(
            row(1, 101L, 12),
            row(2, 102L, 11),
            row(3, 103L, 11),
            row(4, 104L, 6),
            row(5, 105L, 3));

    @Test
    void noEliminationsLeavesTheTimedResult() {
        assertThat(ShikabaneOrdering.apply(TIMED, List.of())).isSameAs(TIMED);
    }

    @Test
    void theDeadComeAfterTheSurvivorsLastToDieFirst() {
        // 104 died first, then 102; 101, 103 and 105 are alive at the end
        List<ResultRow> ordered = ShikabaneOrdering.apply(TIMED, List.of(104L, 102L));

        assertThat(ordered).extracting(ResultRow::entryId).containsExactly(101L, 103L, 105L, 102L, 104L);
        assertThat(ordered).extracting(ResultRow::position).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void aDeadCarWithMoreLapsStillPlacesBehindEverySurvivor() {
        List<ResultRow> ordered = ShikabaneOrdering.apply(TIMED, List.of(101L));

        assertThat(ordered).extracting(ResultRow::entryId).containsExactly(102L, 103L, 104L, 105L, 101L);
    }

    private static ResultRow row(int position, long entryId, int laps) {
        return new ResultRow(position, entryId, null, "Driver " + entryId, String.valueOf(position), laps,
                laps * 25_000L, 24_000L, null);
    }
}
