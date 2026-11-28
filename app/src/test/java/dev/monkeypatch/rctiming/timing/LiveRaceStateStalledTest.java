package dev.monkeypatch.rctiming.timing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A car that stops counting laps is not waited for when the race ends, and is a DNF. */
class LiveRaceStateStalledTest {

    private LiveRaceState state;

    @BeforeEach
    void setUp() {
        state = new LiveRaceState();
    }

    private void pass(long entryId, long seconds, boolean clockExpired) {
        state.applyLapPassing(new LapPassingEvent(1L, "T" + entryId, seconds * 1_000_000L), entryId,
                seconds * 1_000L, clockExpired);
    }

    /** Car 1 and car 2 each run 10 s laps up to 50 s; car 2 then stops. */
    private void carTwoStopsAfterFifty() {
        for (long t = 0; t <= 50; t += 10) {
            pass(1, t, false);
            pass(2, t + 1, false);
        }
        for (long t = 60; t <= 100; t += 10) {
            pass(1, t, false);
        }
    }

    @Test
    void aCarWithNoLapForFourAverageLapsIsStalled() {
        carTwoStopsAfterFifty();

        assertThat(state.isStalled(2, 100_000)).isTrue();
        assertThat(state.isStalled(1, 100_000)).isFalse();
    }

    @Test
    void aCarThatIsOnlyAFewSecondsLateIsNotStalled() {
        carTwoStopsAfterFifty();

        assertThat(state.isStalled(2, 70_000)).isFalse();
    }

    @Test
    void theRaceEndsWhenEveryCarStillRunningHasTakenTheFlag() {
        carTwoStopsAfterFifty();
        pass(1, 110, true);

        assertThat(state.takeFinishRequest()).isTrue();
    }

    @Test
    void theStoppedCarIsADnfOnceTheRaceHasReachedItsEnd() {
        carTwoStopsAfterFifty();
        assertThat(state.isDnf(2, 100_000)).isFalse();

        pass(1, 110, true);
        assertThat(state.isDnf(2, 110_000)).isTrue();
        assertThat(state.isDnf(1, 110_000)).isFalse();
    }

    @Test
    void aStoppedCarThatTakesTheFlagIsNotADnf() {
        carTwoStopsAfterFifty();
        pass(1, 110, true);
        pass(2, 112, true);

        assertThat(state.isDnf(2, 113_000)).isFalse();
    }

    @Test
    void turningTheCheckOffWaitsForEveryCar() {
        state.setStalledAfterLaps(0);
        carTwoStopsAfterFifty();
        pass(1, 110, true);

        assertThat(state.takeFinishRequest()).isFalse();
    }

    @Test
    void aPauseIsNotHeldAgainstTheCars() {
        carTwoStopsAfterFifty();
        state.pauseStallClocks(70_000);
        // Stopped at 70 s, finished at 400 s: the cars are judged as of the stop
        assertThat(state.isStalled(2, 400_000)).isFalse();

        state.restartStallClocks(400_000);
        assertThat(state.isStalled(2, 400_000)).isFalse();
        assertThat(state.isStalled(2, 480_000)).isTrue();
    }
}
