package dev.monkeypatch.rctiming.domain.result;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Putting cars marked as not started at the bottom of a heat's result. */
class DnsOrderingTest {

    private static final List<ResultRow> ROWS = List.of(
            row(1, 101L, 12, 300_100L),
            row(2, 102L, 0, 0L),
            row(3, 103L, 11, 299_000L));

    @Test
    void nothingMarkedLeavesTheResultAsItWas() {
        assertThat(DnsOrdering.apply(ROWS, Set.of())).isSameAs(ROWS);
    }

    @Test
    void aMarkedCarGoesLastWithNoLapsAndTheOthersCloseUp() {
        List<ResultRow> out = DnsOrdering.apply(ROWS, Set.of(102L));

        assertThat(out).extracting(ResultRow::entryId).containsExactly(101L, 103L, 102L);
        assertThat(out).extracting(ResultRow::position).containsExactly(1, 2, 3);
        assertThat(out.get(2).dns()).isTrue();
        assertThat(out.get(2).lapsCompleted()).isZero();
        assertThat(out.get(0).dns()).isFalse();
    }

    @Test
    void aMarkedCarThatHadTimedLapsIsStillLastWithNoLaps() {
        List<ResultRow> out = DnsOrdering.apply(ROWS, Set.of(101L));

        assertThat(out.get(2).entryId()).isEqualTo(101L);
        assertThat(out.get(2).lapsCompleted()).isZero();
        assertThat(out.get(2).totalTimeMs()).isZero();
        assertThat(out.get(2).dns()).isTrue();
    }

    @Test
    void severalMarkedCarsKeepTheirOrderAtTheBottom() {
        List<ResultRow> out = DnsOrdering.apply(ROWS, Set.of(101L, 102L));

        assertThat(out).extracting(ResultRow::entryId).containsExactly(103L, 101L, 102L);
    }

    private static ResultRow row(int position, long entryId, int laps, long timeMs) {
        return new ResultRow(position, entryId, null, "Driver " + entryId, null, laps, timeMs, null, null);
    }
}
