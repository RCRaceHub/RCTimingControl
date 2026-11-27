package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** With times cut to hundredths, cars with the same laps and the same last crossing share a place, as the rules say. */
class LiveRaceStateSharedPlacesTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
        state.setSharedPlacesOnTies(true);
    }

    private void pass(long entryId, long millis) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, millis * 1_000L), entryId, millis, false);
    }

    private List<Integer> placesInOrder() {
        return state.calculatePositions().stream().map(LiveTimingRowDto::position).toList();
    }

    @Test
    void carsWithTheSameLapsAndTimeShareAPlaceAndTheNextTakesItsOwn() {
        pass(1, 1_000);
        pass(2, 1_000);
        pass(3, 1_000);
        pass(1, 61_000);
        pass(2, 61_000);
        pass(3, 61_010);

        assertThat(placesInOrder()).containsExactly(1, 1, 3);
    }

    @Test
    void aCarOneLapDownDoesNotShareAPlaceWithACarOnTheLeadLap() {
        pass(1, 1_000);
        pass(2, 1_000);
        pass(1, 61_000);

        assertThat(placesInOrder()).containsExactly(1, 2);
    }

    @Test
    void theOrderIsOneBeforeTheOtherWhenTheOptionIsOff() {
        state.setSharedPlacesOnTies(false);
        pass(1, 1_000);
        pass(2, 1_000);
        pass(1, 61_000);
        pass(2, 61_000);

        assertThat(placesInOrder()).containsExactly(1, 2);
    }
}
