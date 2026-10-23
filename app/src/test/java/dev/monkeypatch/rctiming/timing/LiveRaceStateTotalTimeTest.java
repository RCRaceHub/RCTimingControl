package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The total time on a live row runs from the race's start to the car's last crossing, as the stored result does. */
class LiveRaceStateTotalTimeTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
    }

    private void pass(long entryId, long seconds) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId,
                seconds * 1_000L, false);
    }

    private Long totalOf(long entryId) {
        return state.calculatePositions().stream().filter(r -> r.entryId() == entryId)
                .findFirst().orElseThrow().totalTimeMs();
    }

    @Test
    void runsFromTheRaceStartToTheLastCrossing() {
        state.putRaceStart(100_000L, false);
        pass(1, 110);
        pass(1, 140);

        assertThat(totalOf(1)).isEqualTo(40_000L);
    }

    @Test
    void aRollingStartCountsFromTheFirstCrossing() {
        state.putRaceStart(100_000L, true);
        pass(1, 110);
        pass(2, 112);
        pass(1, 140);

        assertThat(totalOf(1)).isEqualTo(30_000L);
        assertThat(totalOf(2)).isEqualTo(2_000L);
    }

    @Test
    void isNullWhenTheStartIsNotKnown() {
        pass(1, 110);

        assertThat(totalOf(1)).isNull();
    }
}
