package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRaceStateStaggerStartTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
        state.putStartOffsets(Map.of(1L, 0L, 2L, 5_000L));
    }

    @Test
    void aCarCrossingAfterItsSlotStartsItsTimeAtTheCrossing() {
        assertThat(state.onStaggerCrossing(2, 20_000, null)).isTrue();

        assertThat(state.startOffsetMs(2)).isEqualTo(20_000);
        assertThat(state.lastStartOffsetMs()).isEqualTo(20_000);
    }

    @Test
    void aCarCrossingBeforeItsSlotKeepsTheSlot() {
        assertThat(state.onStaggerCrossing(2, 3_000, null)).isFalse();

        assertThat(state.startOffsetMs(2)).isEqualTo(5_000);
    }

    @Test
    void onlyTheFirstCrossingSettlesTheStart() {
        state.onStaggerCrossing(2, 8_000, null);
        assertThat(state.onStaggerCrossing(2, 60_000, null)).isFalse();

        assertThat(state.startOffsetMs(2)).isEqualTo(8_000);
    }

    @Test
    void aLateStartIsLimitedToTheMostItMaySlip() {
        state.onStaggerCrossing(2, 200_000, 90_000L);

        assertThat(state.startOffsetMs(2)).isEqualTo(95_000);
    }

    @Test
    void anEntryWithNoSlotIsLeftAlone() {
        assertThat(state.onStaggerCrossing(9, 20_000, null)).isFalse();
    }
}
