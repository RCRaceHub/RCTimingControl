package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A time penalty is added to the car's time in live positions, gaps and total, but not to the result as timed. */
class LiveRaceStateTimePenaltyTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
        state.putRaceStart(100_000L, false);
        // Car 1 leads car 2 by 2 s, both on one lap
        pass(1, 110);
        pass(2, 112);
        pass(1, 140);
        pass(2, 143);
    }

    private void pass(long entryId, long seconds) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId,
                seconds * 1_000L, false);
    }

    private static LiveTimingRowDto row(List<LiveTimingRowDto> rows, long entryId) {
        return rows.stream().filter(r -> r.entryId() == entryId).findFirst().orElseThrow();
    }

    @Test
    void aPenaltyBiggerThanTheGapChangesTheOrder() {
        state.applyTimePenalty(1, 5_000L);

        List<LiveTimingRowDto> rows = state.calculatePositions();
        assertThat(rows.get(0).entryId()).isEqualTo(2);
        assertThat(row(rows, 1).position()).isEqualTo(2);
        assertThat(row(rows, 1).timePenaltyMs()).isEqualTo(5_000L);
        assertThat(row(rows, 1).totalTimeMs()).isEqualTo(45_000L);
        assertThat(row(rows, 1).gapToLeaderMs()).isEqualTo(2_000L);
    }

    @Test
    void aSmallerPenaltyKeepsTheOrderAndNarrowsTheGap() {
        state.applyTimePenalty(1, 500L);

        List<LiveTimingRowDto> rows = state.calculatePositions();
        assertThat(rows.get(0).entryId()).isEqualTo(1);
        assertThat(row(rows, 2).gapToLeaderMs()).isEqualTo(2_500L);
    }

    @Test
    void penaltiesAddUp() {
        state.applyTimePenalty(1, 2_000L);
        state.applyTimePenalty(1, 2_000L);

        assertThat(row(state.calculatePositions(), 1).timePenaltyMs()).isEqualTo(4_000L);
    }

    @Test
    void theResultAsTimedLeavesPenaltiesOut() {
        state.applyTimePenalty(1, 5_000L);

        List<LiveTimingRowDto> rows = state.calculateTimedPositions();
        assertThat(rows.get(0).entryId()).isEqualTo(1);
        assertThat(row(rows, 1).totalTimeMs()).isEqualTo(40_000L);
        assertThat(row(rows, 1).timePenaltyMs()).isZero();
    }

    @Test
    void aPenaltyDoesNotPutACarAheadOfOneWithMoreLaps() {
        pass(2, 170);
        state.applyTimePenalty(2, 1_000L);

        assertThat(state.calculatePositions().get(0).entryId()).isEqualTo(2);
    }

    @Test
    void aZeroPenaltyIsIgnored() {
        state.applyTimePenalty(1, 0L);

        assertThat(row(state.calculatePositions(), 1).timePenaltyMs()).isZero();
    }
}
