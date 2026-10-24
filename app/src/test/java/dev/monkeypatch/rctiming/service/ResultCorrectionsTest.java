package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Re-ranking a finished race's result after corrections (#63). */
class ResultCorrectionsTest {

    private static final List<ResultRow> TIMED = List.of(
            row(1, 101L, 12, 300_100L, null),
            row(2, 102L, 12, 301_900L, 1_800L),
            row(3, 103L, 11, 299_000L, null));

    @Test
    void nothingToApplyLeavesTheResultAsTimed() {
        assertThat(ResultCorrections.apply(TIMED, Map.of(), Map.of())).isSameAs(TIMED);
        assertThat(ResultCorrections.apply(TIMED, Map.of(101L, 0), Map.of(102L, 0L))).isSameAs(TIMED);
    }

    @Test
    void aTimePenaltyCanDropADriverBehindAnotherOnTheSameLap() {
        List<ResultRow> corrected = ResultCorrections.apply(TIMED, Map.of(), Map.of(101L, 5_000L));

        assertThat(corrected).extracting(ResultRow::entryId).containsExactly(102L, 101L, 103L);
        assertThat(corrected).extracting(ResultRow::position).containsExactly(1, 2, 3);
        assertThat(corrected.get(1).totalTimeMs()).isEqualTo(305_100L);
        assertThat(corrected.get(0).gapToLeaderMs()).isNull();
        assertThat(corrected.get(1).gapToLeaderMs()).isEqualTo(3_200L);
        // A car a lap down has no time gap, as in live timing
        assertThat(corrected.get(2).gapToLeaderMs()).isNull();
    }

    @Test
    void lapChangesReRankByLapsFirst() {
        List<ResultRow> corrected = ResultCorrections.apply(TIMED, Map.of(101L, -1, 103L, 2), Map.of());

        assertThat(corrected).extracting(ResultRow::entryId).containsExactly(103L, 102L, 101L);
        assertThat(corrected).extracting(ResultRow::lapsCompleted).containsExactly(13, 12, 11);
    }

    @Test
    void lapsNeverGoBelowZero() {
        List<ResultRow> corrected = ResultCorrections.apply(TIMED, Map.of(103L, -20), Map.of());

        assertThat(corrected.get(2).entryId()).isEqualTo(103L);
        assertThat(corrected.get(2).lapsCompleted()).isZero();
    }

    @Test
    void aDriverWithNoRecordedTimeGoesBehindOthersOnTheSameLaps() {
        List<ResultRow> timed = List.of(row(1, 101L, 1, 30_000L, null), row(2, 104L, 0, 0L, null));

        List<ResultRow> corrected = ResultCorrections.apply(timed, Map.of(104L, 1), Map.of());

        assertThat(corrected).extracting(ResultRow::entryId).containsExactly(101L, 104L);
        assertThat(corrected.get(1).gapToLeaderMs()).isNull();
    }

    @Test
    void aTimePenaltyDoesNotGiveATimeToADriverWithNone() {
        List<ResultRow> timed = List.of(row(1, 101L, 1, 30_000L, null), row(2, 104L, 0, 0L, null));

        List<ResultRow> corrected = ResultCorrections.apply(timed, Map.of(104L, 1), Map.of(104L, 5_000L));

        assertThat(corrected).extracting(ResultRow::entryId).containsExactly(101L, 104L);
        assertThat(corrected.get(1).totalTimeMs()).isZero();
    }

    @Test
    void aCarThatDidNotFinishStillDidNotFinishAfterACorrection() {
        List<ResultRow> timed = List.of(row(1, 101L, 12, 300_100L, null),
                row(2, 102L, 11, 301_900L, null).withDnf(true));

        List<ResultRow> corrected = ResultCorrections.apply(timed, Map.of(), Map.of(101L, 1_000L));

        assertThat(corrected).extracting(ResultRow::dnf).containsExactly(false, true);
    }

    private static ResultRow row(int position, long entryId, int laps, long totalTimeMs, Long gap) {
        return new ResultRow(position, entryId, null, "Driver " + entryId, String.valueOf(position), laps,
                totalTimeMs, 24_000L, gap);
    }
}
