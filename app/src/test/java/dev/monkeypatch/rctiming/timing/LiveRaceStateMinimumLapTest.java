package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRaceStateMinimumLapTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
        state.putMinLaps(Map.of(1L, 10_000L));
    }

    private void pass(long entryId, long seconds) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId);
    }

    @Test
    void aPassingSoonerThanTheMinimumLapIsIgnored() {
        pass(1L, 100);
        pass(1L, 103); // a double read
        pass(1L, 125);

        LiveRacePosition position = state.positions.get(1L);
        assertThat(position.getLapsCompleted()).isEqualTo(2);
        // Timed from the last lap that counted, not from the ignored read
        assertThat(position.getLastLapMs()).isEqualTo(25_000L);
    }

    @Test
    void theFirstPassingIsNeverIgnored() {
        pass(1L, 100);

        assertThat(state.positions.get(1L).getLapsCompleted()).isEqualTo(1);
    }

    @Test
    void aLapExactlyTheMinimumCounts() {
        pass(1L, 100);
        pass(1L, 110);

        assertThat(state.positions.get(1L).getLapsCompleted()).isEqualTo(2);
    }

    @Test
    void anEntryWithNoMinimumIsNeverFiltered() {
        pass(2L, 100);
        pass(2L, 101);

        assertThat(state.positions.get(2L).getLapsCompleted()).isEqualTo(2);
    }

    @Test
    void anIgnoredPassingDoesNotCountAsTheLastLap() {
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 100_000_000L), 1L, 5_000L);
        state.applyLapPassing(new LapPassingEvent(1L, "T1", 103_000_000L), 1L, 8_000L);

        assertThat(state.lastLapCreditedAtMs()).contains(5_000L);
    }
}
